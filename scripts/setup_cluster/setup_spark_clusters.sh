#!/bin/bash
# Define color variables
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
RESET='\033[0m'  # Reset color
# Read configuration from the input file
read_config() {
    CONFIG_FILE=$1
    echo -e "${CYAN}Reading configuration from ${YELLOW}$CONFIG_FILE${RESET}..."

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
            master_site_name)
                MASTER_SITE=$value
                echo -e "${GREEN}Master site name set to: ${YELLOW}$MASTER_SITE${RESET}"
                ;;
            master_node_name)
                MASTER_NODE=$value
                echo -e "${GREEN}Master node name set to: ${YELLOW}$MASTER_NODE${RESET}"
                ;;
            master_node_ip)
                MASTER_IP=$value
                echo -e "${GREEN}Master node IP set to: ${YELLOW}$MASTER_IP${RESET}"
                ;;
            master_node_port)
                MASTER_PORT=$value
                echo -e "${GREEN}Master node port set to: ${YELLOW}$MASTER_PORT${RESET}"
                ;;
            worker_site_name)
                WORKER_SITE=$value
                echo -e "${GREEN}Worker site name set to: ${YELLOW}$WORKER_SITE${RESET}"
                ;;
            worker_node_name)
                WORKER_NODE=$value
                WORKERS+=("$WORKER_SITE:$WORKER_NODE")
                echo -e "${GREEN}Added worker: ${YELLOW}$WORKER_SITE:$WORKER_NODE${RESET}"
                ;;
        esac
    done < "$CONFIG_FILE"

    echo -e "${CYAN}Configuration reading complete.${RESET}"
}

# Multi-hop SSH execution
ssh_exec() {
    SITE=$1
    NODE=$2
    CMD=$3
    echo -e "${CYAN}Executing on ${YELLOW}$SITE${CYAN} -> ${YELLOW}$NODE${CYAN}: ${MAGENTA}$CMD${RESET}"
    ssh $USER_NAME@access.grid5000.fr "ssh $SITE 'ssh $NODE \"$CMD\"'"
}

# Multi-hop SCP execution
scp_exec() {
    LOCAL_FILE=$1
    SITE=$2
    REMOTE_PATH=$3
    echo -e "${CYAN}Copying ${YELLOW}$LOCAL_FILE${CYAN} to ${YELLOW}$SITE${CYAN} : ${YELLOW}$REMOTE_PATH${RESET}"

    # Use SCP to copy the file directly to the target node through access.grid5000.fr
    scp $LOCAL_FILE $USER_NAME@access.grid5000.fr:$SITE/$REMOTE_PATH
}

# Set up the Spark master
setup_master() {
    echo -e "${CYAN}Setting up Spark master on ${YELLOW}$MASTER_NODE${CYAN} (${YELLOW}$MASTER_SITE${CYAN})...${RESET}"

    # Step 1: Create the local script
    LOCAL_SCRIPT="/tmp/setup_spark_master.sh"
    cat << EOF > $LOCAL_SCRIPT
#!/bin/bash
echo "export SPARK_MASTER_HOST=$MASTER_IP" > $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_MASTER_PORT=$MASTER_PORT" >> $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_WORKER_INSTANCES=1" >> $SPARK_HOME/conf/spark-env.sh
$SPARK_HOME/sbin/stop-master.sh
$SPARK_HOME/sbin/start-master.sh
$SPARK_HOME/sbin/stop-worker.sh
$SPARK_HOME/sbin/start-worker.sh spark://$MASTER_IP:$MASTER_PORT
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
    LOCAL_SCRIPT="/tmp/setup_spark_worker.sh"
    cat << EOF > $LOCAL_SCRIPT
#!/bin/bash
echo "export SPARK_MASTER=spark://$MASTER_IP:$MASTER_PORT" > $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_WORKER_WEBUI_PORT=8080" >> $SPARK_HOME/conf/spark-env.sh
echo "export SPARK_WORKER_INSTANCES=1" >> $SPARK_HOME/conf/spark-env.sh
$SPARK_HOME/sbin/stop-worker.sh
$SPARK_HOME/sbin/start-worker.sh spark://$MASTER_IP:$MASTER_PORT
EOF
    local i=0
    while [ $i -lt ${#WORKERS[@]} ]; do
        SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
        NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
        echo -e "${CYAN}Setting up Spark worker on ${YELLOW}$NODE${CYAN} (${YELLOW}$SITE${CYAN})...${RESET}"

        # Step 2: Copy the script to the remote worker node
        scp_exec $LOCAL_SCRIPT "$SITE" "setup_spark_worker_$NODE.sh"

        # Step 3: Run the script remotely on the worker node
        ssh_exec "$SITE" "$NODE" "
            chmod +x setup_spark_worker_$NODE.sh &&
            ./setup_spark_worker_$NODE.sh &&
            rm -f setup_spark_worker_$NODE.sh
        " &
        i=$((i + 1))
    done 
    wait
    rm -f $LOCAL_SCRIPT
}

# Cloning repo from github
clone_repo() {
    SITE=$1
    NODE=$2
    echo -e "${CYAN}Cloning repo on ${YELLOW}$NODE${CYAN} (${YELLOW}$SITE${CYAN})...${RESET}"
    ssh_exec "$SITE" "$NODE" "
        rm -rf ~/systemes-distribues/
        git clone https://github.com/abdonasmane/systemes-distribues.git
    "
    ssh_exec "$SITE" "$NODE" "
        cd ~/systemes-distribues/src/test/resources/test6/
        ./generateUnbalancedTreeMakefile.py 50_000_000 1000
        cd ~/systemes-distribues/src/test/resources/test7/
        ./generateAllToAllTree.py 50_000_000 3 1000
        cd ~/systemes-distribues/src/test/resources/test8/
        ./generateATATvarTargetsPerLev.py 50_000_000 3 1000
        cd ~/systemes-distribues/src/test/resources/test9/
        ./generateHeavyTasks.py 3952
        rm -rf compressed_alea_tests  
    "
}

# copying cloned repo to /tmp
copy_repo_to_tmp() {
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "
        rm -rf /tmp/systemes-distribues
        cp -r ~/systemes-distribues /tmp
    " &
    local i=0
    while [ $i -lt ${#WORKERS[@]} ]; do
        SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
        NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
        echo -e "${CYAN}Copying repo to /tmp on node ${YELLOW}$NODE${CYAN} (${YELLOW}$SITE${CYAN})...${RESET}"

        ssh_exec "$SITE" "$NODE" "
            rm -rf /tmp/systemes-distribues
            cp -r ~/systemes-distribues /tmp
        " &
        i=$((i + 1))
    done
    wait
}

# Common setup for all nodes
common_setup() {
    SITE=$1
    NODE=$2
    echo -e "${CYAN}Installing Maven and preparing project on ${YELLOW}$NODE${CYAN} (${YELLOW}$SITE${CYAN})...${RESET}"
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
    echo -e "${CYAN}Launching ServeFile on ${YELLOW}$NODE${CYAN} (${YELLOW}$SITE${CYAN})...${RESET}"
    # kill process if it's already running
    ssh_exec "$SITE" "$NODE" "pkill -f java\ ServeFile\ 8888"
    DIRECTORY_PATH=$(dirname "$PATH_TO_TARGET")
    ssh_exec "$SITE" "$NODE" "cd $TARGET_PATH && java ServeFile 8888 $DIRECTORY_PATH" &
}

# Launch FileLocatorServer on the master
launch_file_locator_server() {
    PATH_TO_TARGET=$1
    DIRECTORY_PATH=$(dirname "$PATH_TO_TARGET")
    echo -e "${CYAN}Launching FileLocatorServer on ${YELLOW}$MASTER_NODE${CYAN} (${YELLOW}$MASTER_SITE${CYAN})...${RESET}"
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "pkill -f java\ FileLocatorServer\ 9999"
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "cd $TARGET_PATH && java  FileLocatorServer 9999 $DIRECTORY_PATH" &
}

# Submit Spark application
submit_spark_app() {
    PATH_TO_TARGET=$1
    NFS_MODE=$2
    echo -e "${CYAN}Submitting Spark app from ${YELLOW}$MASTER_NODE${CYAN} (${YELLOW}$MASTER_SITE${CYAN})...${RESET}"
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "
        $SPARK_HOME/bin/spark-submit --master spark://$MASTER_IP:$MASTER_PORT --driver-memory 50G --executor-memory 50G --conf 'spark.executor.extraJavaOptions=-XX:-UseGCOverheadLimit' --conf 'spark.driver.extraJavaOptions=-XX:-UseGCOverheadLimit' --deploy-mode client --class Main $PROJECT_HOME/target/distributed-make-project-1.0.jar $PATH_TO_TARGET $EXECUTED_TARGET spark://$MASTER_IP:$MASTER_PORT $NFS_MODE
    "
}

# Open WebUi for spark
open_spark_webui() {
    echo -e "${CYAN}Opening Spark WebUI pages...${RESET}"
    WEBUI_URL="http://$MASTER_NODE.$MASTER_SITE.http8080.proxy.grid5000.fr/"
    open "$WEBUI_URL"
    # xdg-open "$WEBUI_URL"
    echo -e "${CYAN}Opened WebUI for ${YELLOW}$MASTER_NODE${CYAN} on ${YELLOW}$MASTER_SITE${CYAN}: $WEBUI_URL${RESET}"

    # Iterate through each worker node in the WORKERS array
    for worker in "${WORKERS[@]}"; do
        # Split the site and node (toulouse:montcalm-5 -> SITE=toulouse, NODE=montcalm-5)
        SITE=$(echo $worker | cut -d ':' -f 1)
        NODE=$(echo $worker | cut -d ':' -f 2)

        # Construct the WebUI URL
        WEBUI_URL="http://$NODE.$SITE.http8080.proxy.grid5000.fr/"

        # Open the URL in the default browser
        # For macOS (use 'open')
        open "$WEBUI_URL"
        
        # For Linux (use 'xdg-open')
        # xdg-open "$WEBUI_URL"
        
        echo -e "${CYAN}Opened WebUI for ${YELLOW}$NODE${CYAN} on ${YELLOW}$SITE${CYAN}: $WEBUI_URL${RESET}"
    done
}

# Main script execution
main() {
    # here you can specify nodes
    CONFIG_FILE=$1
    # this is the absolute target to the makefile, on the master node
    PATH_TO_TARGET=$2
    # path to the location of the git repo, on all machines
    PROJECT_HOME=$3
    # spark home path
    SPARK_HOME=$4
    # the target of the make : Ex clean, all, ....
    EXECUTED_TARGET=$5
    # username on g5k
    USER_NAME=$6
    # if NFS then nodes should be on the same g5k same, to share the same file system
    NFS=$7
    # if TMP, then paths should be on /tmp of nodes, making TMP uncompatible with NFS
    TMP=$8
    TARGET_PATH=$PROJECT_HOME/target/classes

    if [ $# -ne 8 ]; then
        echo "Usage: $0 CONFIG_FILE PATH_TO_TARGET PROJECT_HOME SPARK_HOME EXECUTED_TARGET USER_NAME NFS_MODE=NFS/NO_NFS TMP_MODE=TMP/NO_TMP"
        exit 1
    fi

    if [ "$NFS" == "NFS" ]; then
        echo -e "${GREEN}NFS mode enabled : expecting nodes from the same site${RESET}"
    elif [ "$NFS" == "NO_NFS" ]; then
        echo -e "${YELLOW}NO_NFS mode enabled : nodes can be from different sites${RESET}"
    else
        echo -e "${RED}Invalid mode: NFS=$NFS${RESET}"
        exit 1
    fi

    if [ "$TMP" == "TMP" ]; then
        if [ "$NFS" == "NFS" ]; then
            echo -e "${GREEN}NFS mode not compatible with TMP mode${RESET}"
            exit 1
        fi
        echo -e "${GREEN}TMP mode enabled${RESET}"
    elif [ "$TMP" == "NO_TMP" ]; then
        echo -e "${YELLOW}NO_TMP mode enabled${RESET}"
    else
        echo -e "${RED}Invalid mode TMP: TMP=$TMP${RESET}"
        exit 1
    fi

    read_config "$CONFIG_FILE"

    # Common setup for all nodes
    processed_sites=()
    clone_repo "$MASTER_SITE" "$MASTER_NODE" &
    processed_sites+=("$MASTER_SITE")
    local i=0
    while [ $i -lt ${#WORKERS[@]} ]; do
        SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
        NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
        if [[ ! " ${processed_sites[@]} " =~ " ${SITE} " ]]; then
            clone_repo "$SITE" "$NODE" &
            processed_sites+=("$SITE")
        else
            echo "Skipping $NODE since it has already been processed"
        fi

        i=$((i + 1))
    done
    wait
    if [ "$TMP" == "TMP" ]; then
        copy_repo_to_tmp
    fi
    # common setup based on tmp value
    processed_sites=()
    common_setup "$MASTER_SITE" "$MASTER_NODE" &
    processed_sites+=("$MASTER_SITE")
    local i=0
    while [ $i -lt ${#WORKERS[@]} ]; do
        SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
        NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
        if [[ "$TMP" == "TMP" || ! " ${processed_sites[@]} " =~ " ${SITE} " ]]; then
            common_setup "$SITE" "$NODE" &
            processed_sites+=("$SITE")
        else
            echo "Skipping $NODE since it has already been processed"
        fi

        i=$((i + 1))
    done
    wait
    # Setup Spark master and workers
    setup_master
    setup_workers
    # Launch ServeFile on all nodes
    if [ "$NFS" == "NO_NFS" ]; then
        launch_serve_file "$MASTER_SITE" "$MASTER_NODE" "$PATH_TO_TARGET" &
        local i=0
        while [ $i -lt ${#WORKERS[@]} ]; do
            SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
            NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
            launch_serve_file "$SITE" "$NODE" "$PATH_TO_TARGET" &
            i=$((i + 1))
        done
        launch_file_locator_server "$PATH_TO_TARGET" &
        sleep 6 # wait for servers to start
    fi
    # Submit the Spark application
    submit_spark_app "$PATH_TO_TARGET" "$NFS"

    # open WebUis
    open_spark_webui
}

# Run the main function
main "$@"
