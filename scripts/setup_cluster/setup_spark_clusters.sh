#!/bin/bash

# Read configuration from the input file
read_config() {
    CONFIG_FILE=$1
    echo "Reading configuration from $CONFIG_FILE..."
    
    MASTER_SITE=""
    MASTER_NODE=""
    MASTER_IP=""
    MASTER_PORT=""
    WORKERS=()

    # Temporary variables to track worker site and node names
    WORKER_SITE=""
    WORKER_NODE=""

    # Read the file
    while IFS= read -r line || [[ -n $line ]]; do
        key=$(echo "$line" | cut -d '=' -f 1)
        value=$(echo "$line" | cut -d '=' -f 2)
        case $key in
            master_site_name) MASTER_SITE=$value ;;
            master_node_name) MASTER_NODE=$value ;;
            master_node_ip) MASTER_IP=$value ;;
            master_node_port) MASTER_PORT=$value ;;
            worker_site_name) WORKER_SITE=$value ;;
            worker_node_name)
                WORKER_NODE=$value
                WORKERS+=("$WORKER_SITE:$WORKER_NODE")
                ;;
        esac
    done < "$CONFIG_FILE"
}

# Multi-hop SSH execution
ssh_exec() {
    SITE=$1
    NODE=$2
    CMD=$3
    echo "Executing on $SITE -> $NODE: $CMD"
    ssh almimoun@access.grid5000.fr "ssh $SITE 'ssh $NODE \"$CMD\"'"
}

# Multi-hop SCP execution
scp_exec() {
    LOCAL_FILE=$1
    SITE=$2
    REMOTE_PATH=$3
    echo "Copying $LOCAL_FILE to $SITE : $REMOTE_PATH"

    # Use SCP to copy the file directly to the target node through access.grid5000.fr
    scp $LOCAL_FILE almimoun@access.grid5000.fr:$SITE/$REMOTE_PATH
}


# Set up the Spark master
setup_master() {
    echo "Setting up Spark master on $MASTER_NODE ($MASTER_SITE)..."

    # Step 1: Create the local script
    LOCAL_SCRIPT="/tmp/setup_spark_master.sh"
    cat << EOF > $LOCAL_SCRIPT
#!/bin/bash
sed -i '/^export SPARK_MASTER_HOST/d' $SPARK_HOME/conf/spark-env.sh
sed -i '/^export SPARK_MASTER_PORT/d' $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_MASTER_HOST=$MASTER_IP" >> $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_MASTER_PORT=$MASTER_PORT" >> $SPARK_HOME/conf/spark-env.sh
$SPARK_HOME/sbin/stop-master.sh
$SPARK_HOME/sbin/start-master.sh
EOF
    # Step 2: Copy the script to the remote machine
    scp_exec $LOCAL_SCRIPT "$MASTER_SITE" ""

    # Step 3: Run the script remotely
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "
        chmod +x setup_spark_master.sh &&
        ./setup_spark_master.sh &&
        rm -f setup_spark_master.sh
    "

    # Step 4: Clean up the local script
    rm -f $LOCAL_SCRIPT
}


# Set up Spark workers
setup_workers() {
    local i=0
    while [ $i -lt ${#WORKERS[@]} ]; do
        SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
        NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
        echo "Setting up Spark worker on $NODE ($SITE)..."

        # Step 1: Create the local worker setup script
        LOCAL_SCRIPT="/tmp/setup_spark_worker.sh"
        cat << EOF > $LOCAL_SCRIPT
#!/bin/bash
sed -i '/^export SPARK_MASTER/d' $SPARK_HOME/conf/spark-env.sh
sed -i '/^export SPARK_WORKER_WEBUI_PORT/d' $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_MASTER=spark://$MASTER_IP:$MASTER_PORT" >> $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_WORKER_WEBUI_PORT=8080" >> $SPARK_HOME/conf/spark-env.sh
$SPARK_HOME/sbin/stop-worker.sh
$SPARK_HOME/sbin/start-worker.sh spark://$MASTER_IP:$MASTER_PORT
EOF
        # Step 2: Copy the script to the remote worker node
        scp_exec $LOCAL_SCRIPT "$SITE" ""

        # Step 3: Run the script remotely on the worker node
        ssh_exec "$SITE" "$NODE" "
            chmod +x setup_spark_worker.sh &&
            ./setup_spark_worker.sh &&
            rm -f setup_spark_worker.sh
        "

        # Step 4: Clean up the local script
        rm -f $LOCAL_SCRIPT

        i=$((i + 1))
    done
}

# Common setup for all nodes
common_setup() {
    SITE=$1
    NODE=$2
    echo "Installing Maven and preparing project on $NODE ($SITE)..."
    ssh_exec "$SITE" "$NODE" "
        sudo-g5k apt install -y maven &&
        source ~/.bashrc &&
        cd $PROJECT_HOME &&
        mvn clean package
    "
}

# Launch ServeFile on all nodes
launch_serve_file() {
    SITE=$1
    NODE=$2
    PATH_TO_TARGET=$3
    echo "Launching ServeFile on $NODE ($SITE)..."
    # kill process if it's already running
    ssh_exec "$SITE" "$NODE" "pkill -f java\ ServeFile\ 8888"
    DIRECTORY_PATH=$(dirname "$PATH_TO_TARGET")
    ssh_exec "$SITE" "$NODE" "cd $TARGET_PATH && java ServeFile 8888 $DIRECTORY_PATH" &
}

# Launch FileLocatorServer on the master
launch_file_locator_server() {
    echo "Launching FileLocatorServer on $MASTER_NODE ($MASTER_SITE)..."
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "pkill -f java\ FileLocatorServer\ 9999"
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "cd $TARGET_PATH && java FileLocatorServer 9999" &
}

# Submit Spark application
submit_spark_app() {
    PATH_TO_TARGET=$1
    echo "Submitting Spark app from $MASTER_NODE ($MASTER_SITE)..."
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "
        $SPARK_HOME/bin/spark-submit --master spark://$MASTER_IP:$MASTER_PORT --deploy-mode cluster --class Main $PROJECT_HOME/target/distributed-make-project-1.0.jar $PATH_TO_TARGET spark://$MASTER_IP:$MASTER_PORT
    "
}

# Open WebUi for spark
open_spark_webui() {
    echo "Opening Spark WebUI pages..."
    WEBUI_URL="http://$MASTER_NODE.$MASTER_SITE.http8080.proxy.grid5000.fr/"
    open "$WEBUI_URL"
    echo "Opened WebUI for $MASTER_NODE on $MASTER_SITE: $WEBUI_URL"

    # Iterate through each worker node in the WORKERS array
    for worker in "${WORKERS[@]}"; do
        # Split the site and node (toulouse:montcalm-5 -> SITE=toulouse, NODE=montcalm-5)
        SITE=$(echo $worker | cut -d ':' -f 1)
        NODE=$(echo $worker | cut -d ':' -f 2)

        # Construct the WebUI URL
        WEBUI_URL="http://$NODE.$SITE.http8080.proxy.grid5000.fr/"

        # Open the URL in the default browser
        # For macOS (use 'open')
        # open "$WEBUI_URL"
        
        # For Linux (use 'xdg-open')
        xdg-open "$WEBUI_URL"
        
        echo "Opened WebUI for $NODE on $SITE: $WEBUI_URL"
    done
}

# Main script execution
main() {
    CONFIG_FILE=$1
    PATH_TO_TARGET=$2
    PROJECT_HOME=$3
    SPARK_HOME=$4
    TARGET_PATH=$PROJECT_HOME/target/classes

    if [ $# -ne 4 ]; then
        echo "Usage: $0 CONFIG_FILE PATH_TO_TARGET PROJECT_HOME SPARK_HOME"
        exit 1
    fi

    read_config "$CONFIG_FILE"

    # Common setup for all nodes
    common_setup "$MASTER_SITE" "$MASTER_NODE"
    local i=0
    while [ $i -lt ${#WORKERS[@]} ]; do
        SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
        NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
        common_setup "$SITE" "$NODE"
        i=$((i + 1))
    done

    # Setup Spark master and workers
    setup_master
    setup_workers

    # Launch ServeFile on all nodes
    launch_serve_file "$MASTER_SITE" "$MASTER_NODE" "$PATH_TO_TARGET"
    local i=0
    while [ $i -lt ${#WORKERS[@]} ]; do
        SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
        NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
        launch_serve_file "$SITE" "$NODE" "$PATH_TO_TARGET"
        i=$((i + 1))
    done

    # Launch FileLocatorServer only on the master
    launch_file_locator_server

    # Submit the Spark application
    submit_spark_app "$PATH_TO_TARGET"

    # open WebUis
    open_spark_webui

}

# Run the main function
main "$@"
