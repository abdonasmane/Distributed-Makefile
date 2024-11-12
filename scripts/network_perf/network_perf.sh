#!/bin/bash

# Color variables
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No color

# Step 1: Compile and launch the server program
echo -e "${CYAN}Compiling and launching the server...${NC}"
javac Pong.java
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to compile Pong.java${NC}"
    exit 1
fi

# Run the server in the background
echo -e "${GREEN}Launching server on port 3000...${NC}"
java Pong 3000 &
server_pid=$!
echo -e "${GREEN}Server launched with PID $server_pid${NC}"
sleep 2  # Give the server a moment to start up if needed

# Step 2: Compile the client program
echo -e "${CYAN}Compiling the client...${NC}"
javac Ping.java
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to compile Ping.java${NC}"
    kill $server_pid  # Stop the server if the client fails to compile
    exit 1
fi

# Step 3: Run the client program multiple times and capture timing results
total_time=0
num_runs=10

for i in $(seq 1 $num_runs); do
    echo -e "${YELLOW}Running ping iteration $i...${NC}"

    # Capture the client output
    client_output=$(java Ping 1000000000 localhost 3000)
    
    # Parse the time from client output, assuming it's the only output in seconds
    elapsed=$(echo "$client_output" | grep -oE '[0-9]+(\.[0-9]+)?')
    echo -e "${CYAN}Time for run $i: $elapsed seconds${NC}"

    # Accumulate time
    total_time=$(echo "$total_time + $elapsed" | bc)
done

# Step 4: Calculate and display the average time
average_time=$(echo "scale=3; $total_time / $num_runs" | bc | awk '{printf "%.3f\n", $0}')
echo -e "${GREEN}Average time over $num_runs runs: $average_time seconds${NC}"

# Step 5: Cleanup - Stop the server
echo -e "${CYAN}Stopping the server...${NC}"
kill $server_pid

# Step 6: Cleanup - Remove .class files
echo -e "${CYAN}Cleaning up compiled files...${NC}"
rm -f *.class
echo -e "${GREEN}Cleanup complete.${NC}"
