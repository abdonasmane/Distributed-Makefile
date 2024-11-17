#!/bin/bash

# Color variables
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No color

# Check if args are provided
if [ $# -ne 4 ]; then
    echo -e "${RED}Usage: $0 <server_hostname> <port> <small_file_path> <large_file_path>${NC}"
    exit 1
fi

SERVER_HOSTNAME=$1
PORT=$2
SMALL_FILE_PATH=$3
LARGE_FILE_PATH=$4

LARGE_FILE_SIZE=$(stat -c%s "$LARGE_FILE_PATH")
LARGE_FILE_NAME=$(basename "$LARGE_FILE_PATH")
SMALL_FILE_NAME=$(basename "$SMALL_FILE_PATH")

# Step 1: Start the server remotely on the specified server node
echo -e "${CYAN}Setting the Server remote server...${NC}"
ssh "$SERVER_HOSTNAME" "mkdir -p ~/destination"
scp PongIO.java "$SERVER_HOSTNAME":~/destination/
ssh "$SERVER_HOSTNAME" "cd ~/destination; javac PongIO.java"
echo -e "${GREEN}Launching server on ${SERVER_HOSTNAME}:${PORT}...${NC}"
ssh "$SERVER_HOSTNAME" "cd ~/destination; java PongIO $PORT ./ 1000000 &" &
server_pid=$!
sleep 2  # Give the server a moment to start up

# Step 2: Compile the client program
echo -e "${CYAN}Compiling PingIO.java...${NC}"
javac PingIO.java
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to compile PingIO.java${NC}"
    ssh "$SERVER_HOSTNAME" "kill $server_pid"
    exit 1
fi

# Step 3: Run the client program multiple times and capture timing results
total_timeRTT=0
total_timeBeta=0
num_runs=10

for i in $(seq 1 $num_runs); do
    echo -e "${YELLOW}Running ping iteration $i...${NC}"

    # Capture the client output
    client_output=$(java PingIO "$SMALL_FILE_PATH" 100000 "$SERVER_HOSTNAME" "$PORT")
    
    # Parse the time from client output, assuming it's the only output in seconds
    elapsedRTT=$(echo "$client_output" | grep -oE '[0-9]+(\.[0-9]+)?')
    elapsedBeta=$(echo "scale=9; $elapsedRTT / 2" | bc | awk '{printf "%.9f\n", $0}')
    echo -e "${CYAN}    RTT(0) :    $elapsedRTT seconds${NC}"
    echo -e "${CYAN}    Beta :      $elapsedBeta seconds${NC}"

    # Accumulate time
    total_timeRTT=$(echo "$total_timeRTT + $elapsedRTT" | bc)
    total_timeBeta=$(echo "$total_timeBeta + $elapsedBeta" | bc)

    # delete destination file
    ssh "$SERVER_HOSTNAME" "cd ~/destination;rm $SMALL_FILE_NAME"
done

# Step 4: Calculate and display the average time
average_timeRTT=$(echo "scale=9; $total_timeRTT / $num_runs" | bc | awk '{printf "%.4f\n", $0}')
average_timeBeta=$(echo "scale=9; $total_timeBeta / $num_runs" | bc | awk '{printf "%.4f\n", $0}')
echo -e "${GREEN}Average time over $num_runs runs: "
echo -e "${GREEN}   RTT(0) :    $average_timeRTT seconds${NC}"
echo -e "${GREEN}   Beta :      $average_timeBeta seconds${NC}"


total_timeRTTN=0
total_timeTo_1=0
# Calculating To
for i in $(seq 1 $num_runs); do
    echo -e "${YELLOW}Running ping iteration $i...${NC}"

    # Capture the client output
    client_output=$(java PingIO "$LARGE_FILE_PATH" 100000 "$SERVER_HOSTNAME" "$PORT")
    
    # Parse the time from client output, assuming it's the only output in seconds
    elapsedRTTN=$(echo "$client_output" | grep -oE '[0-9]+(\.[0-9]+)?')
    elapsedTo_1=$(echo "scale=9; $LARGE_FILE_SIZE / ($elapsedRTTN - 2 * $average_timeBeta)" | bc | awk '{printf "%.9f\n", $0}')
    echo -e "${CYAN}    RTT(N) :    $elapsedRTTN seconds${NC}"
    echo -e "${CYAN}    1/To :      $elapsedTo_1 bytes/sec${NC}"

    # Accumulate time
    total_timeRTTN=$(echo "$total_timeRTTN + $elapsedRTTN" | bc)
    total_timeTo_1=$(echo "$total_timeTo_1 + $elapsedTo_1" | bc)

    # delete destination file
    ssh "$SERVER_HOSTNAME" "cd ~/destination;rm $LARGE_FILE_NAME"
done

average_timeRTTN=$(echo "scale=9; $total_timeRTTN / $num_runs" | bc | awk '{printf "%.4f\n", $0}')
average_timeTo_1=$(echo "scale=9; $total_timeTo_1 / $num_runs" | bc | awk '{printf "%.4f\n", $0}')
echo -e "${GREEN}Average time over $num_runs runs: "
echo -e "${GREEN}   Beta :      $average_timeBeta seconds${NC}"
echo -e "${GREEN}   RTT(N) :    $average_timeRTTN seconds${NC}"
echo -e "${GREEN}   1/To :      $average_timeTo_1 bytes/sec${NC}"

# Step 5: Cleanup - Stop the server on the remote machine
echo -e "${CYAN}Stopping the server on ${SERVER_HOSTNAME}...${NC}"
ssh "$SERVER_HOSTNAME" "pkill -f 'java PongIO $PORT ./ 1000000'"
ssh "$SERVER_HOSTNAME" "rm -rf ~/destination"

# Step 6: Cleanup - Remove .class files
echo -e "${CYAN}Cleaning up compiled files...${NC}"
rm -f *.class
echo -e "${GREEN}Cleanup complete.${NC}"
