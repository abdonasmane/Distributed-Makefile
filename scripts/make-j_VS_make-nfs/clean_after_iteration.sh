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

# runs make clean in all directories
run_make_clean() {
    DIRECTORY_PATH=$(dirname "$PATH_TO_TARGET")
    if [ "$NFS" == "NO_NFS" ]; then
        processed_sites=()
        ssh_exec "$MASTER_SITE" "$MASTER_NODE" "
            cd $DIRECTORY_PATH
            make clean
        " &
        processed_sites+=("$MASTER_SITE")
        local i=0
        while [ $i -lt ${#WORKERS[@]} ]; do
            SITE=$(echo ${WORKERS[$i]} | cut -d ':' -f 1)
            NODE=$(echo ${WORKERS[$i]} | cut -d ':' -f 2)
            if [[ "$TMP" == "TMP" || ! " ${processed_sites[@]} " =~ " ${SITE} " ]]; then
                ssh_exec "$SITE" "$NODE" "
                    cd $DIRECTORY_PATH
                    make clean
                " &
                processed_sites+=("$SITE")
            else
                echo "Skipping $NODE since it has already been processed"
            fi

            i=$((i + 1))
        done
        wait
    else
        ssh_exec "$MASTER_SITE" "$MASTER_NODE" "
            cd $DIRECTORY_PATH
            make clean
        "
    fi
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
    CONFIG_FILE=$2
    PATH_TO_TARGET=$3
    NFS=$4
    TMP=$5
    if [ $# -ne 5 ]; then
        echo "Usage: $0 USER_NAME CONFIG_FILE PATH_TO_TARGET NFS=NFS/NO_NFS TMP=TMP/NO_TMP"
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

    read_config
    run_make_clean
}

# Run the main function
main "$@"