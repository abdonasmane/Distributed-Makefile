#!/bin/bash
# Define color variables
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
RESET='\033[0m'

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

kill_all() {
    local i=0
    while [ $i -lt ${#WORKERS[@]} ]; do
        SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
        NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
        echo -e "${GREEN}Killing worker on ${YELLOW}$NODE${CYAN} (${YELLOW}$SITE${CYAN})...${RESET}"

        ssh_exec "$SITE" "$NODE" "
            cd ~/spark-3.5.3-bin-hadoop3/sbin/ &&
            ./stop-all.sh
        " &
        i=$((i + 1))
    done
    echo -e "${GREEN}Killing master on ${YELLOW}$MASTER_NODE${CYAN} (${YELLOW}$MASTER_SITE${CYAN})...${RESET}"
    ssh_exec "$MASTER_SITE" "$MASTER_NODE" "
        cd ~/spark-3.5.3-bin-hadoop3/sbin/ &&
        ./stop-all.sh
    " &
}

# Multi-hop SSH execution
ssh_exec() {
    SITE=$1
    NODE=$2
    CMD=$3
    echo -e "${CYAN}Executing on ${YELLOW}$SITE${CYAN} -> ${YELLOW}$NODE${CYAN}: ${MAGENTA}$CMD${RESET}"
    ssh $USER_NAME@access.grid5000.fr "ssh $SITE 'ssh $NODE \"$CMD\"'"
}

# Main script execution
main() {
    USER_NAME=$1
    KILL_FILE=$2
    if [ $# -ne 2 ]; then
        echo "Usage: $0 USER_NAME KILL_FILE"
        exit 1
    fi

    read_config "$KILL_FILE"
    kill_all
}

# Run the main function
main "$@"