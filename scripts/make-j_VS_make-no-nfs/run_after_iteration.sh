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

# kill servers
kill_serve_file() {
    SITE=$1
    NODE=$2
    PATH_TO_TARGET=$3
    echo -e "${CYAN}Killing ServeFile on ${YELLOW}$NODE${CYAN} (${YELLOW}$SITE${CYAN})...${RESET}"
    # kill process if it's already running
    ssh_exec "$SITE" "$NODE" "pkill -f java\ ServeFile\ 8888"
}

kill_file_locator_server() {
    PATH_TO_TARGET=$1
    DIRECTORY_PATH=$(dirname "$PATH_TO_TARGET")
    echo -e "${CYAN}Killing FileLocatorServer on ${YELLOW}$MASTER_NODE${CYAN} (${YELLOW}$MASTER_SITE${CYAN})...${RESET}"
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "pkill -f java\ FileLocatorServer\ 9999"
}

# Launch ServeFile on all nodes
launch_serve_file() {
    SITE=$1
    NODE=$2
    PATH_TO_TARGET=$3
    echo -e "${CYAN}Launching ServeFile on ${YELLOW}$NODE${CYAN} (${YELLOW}$SITE${CYAN})...${RESET}"
    DIRECTORY_PATH=$(dirname "$PATH_TO_TARGET")
    ssh_exec "$SITE" "$NODE" "cd $TARGET_PATH && java ServeFile 8888 $DIRECTORY_PATH" &
}

# Launch FileLocatorServer on the master
launch_file_locator_server() {
    PATH_TO_TARGET=$1
    DIRECTORY_PATH=$(dirname "$PATH_TO_TARGET")
    echo -e "${CYAN}Launching FileLocatorServer on ${YELLOW}$MASTER_NODE${CYAN} (${YELLOW}$MASTER_SITE${CYAN})...${RESET}"
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "cd $TARGET_PATH && java FileLocatorServer 9999 $DIRECTORY_PATH" &
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

# Main script execution
main() {
    CONFIG_FILE=$1
    PATH_TO_TARGET=$2
    PROJECT_HOME=$3
    SPARK_HOME=$4
    EXECUTED_TARGET=$5
    USER_NAME=$6
    NFS=$7
    TARGET_PATH=$PROJECT_HOME/target/classes

    if [ $# -ne 7 ]; then
        echo "Usage: $0 CONFIG_FILE PATH_TO_TARGET PROJECT_HOME SPARK_HOME EXECUTED_TARGET USER_NAME NFS_MODE=NFS/NO_NFS"
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

    read_config "$CONFIG_FILE"

    # verify if servers killed
    if [ "$NFS" == "NO_NFS" ]; then
        kill_serve_file "$MASTER_SITE" "$MASTER_NODE" "$PATH_TO_TARGET" &
        local i=0
        while [ $i -lt ${#WORKERS[@]} ]; do
            SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
            NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
            kill_serve_file "$SITE" "$NODE" "$PATH_TO_TARGET" &
            i=$((i + 1))
        done
        kill_file_locator_server "$PATH_TO_TARGET" &
    fi
    wait
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
    if [ "$NFS" == "NO_NFS" ]; then
        kill_serve_file "$MASTER_SITE" "$MASTER_NODE" "$PATH_TO_TARGET" &
        local i=0
        while [ $i -lt ${#WORKERS[@]} ]; do
            SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
            NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
            kill_serve_file "$SITE" "$NODE" "$PATH_TO_TARGET" &
            i=$((i + 1))
        done
        kill_file_locator_server "$PATH_TO_TARGET" &
    fi
}

# Run the main function
main "$@"
