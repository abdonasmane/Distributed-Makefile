#!/usr/bin/env python3
import os
import ast
import matplotlib.pyplot as plt
import sys

# Ensure the test directory is passed as an argument
if len(sys.argv) != 2:
    print("Usage: python3 plot_execution_time.py <test_directory_suffix>")
    sys.exit(1)

# Parameters
test_suffix = sys.argv[1]
output_dir = f"output_logs_{test_suffix}"
seq_time_file = f"{output_dir}/seq_time_file.txt"
graph_filename = f"{output_dir}/perf_graph_{test_suffix}.png"

# Check if the directory exists
if not os.path.exists(output_dir):
    print(f"Error: Directory {output_dir} does not exist.")
    sys.exit(1)

if not os.path.exists(seq_time_file):
    print(f"Error: Sequential time file {seq_time_file} does not exist.")
    sys.exit(1)

# Read the sequential time from the file
with open(seq_time_file, "r") as seq_file:
    seq_time = float(seq_file.read().strip())

# Variables to store results
machine_counts = []
execution_times = []
seq_times = []

# Iterate over the subdirectories corresponding to the number of machines
for i in range(len(os.listdir(output_dir))):
    times_filename = f"{output_dir}/with_{i+1}_machines/times_iteration_{i}.txt"
    
    if not os.path.exists(times_filename):
        print(f"Warning: {times_filename} does not exist. Skipping iteration {i}.")
        continue
    
    # Read and process the times file
    with open(times_filename, "r") as times_file:
        times_data = ast.literal_eval(times_file.read().strip())
        
        # Extract global execution time
        global_time = times_data[0]
        error_logs = times_data[-1]
        
        if error_logs != "[]":
            global_time = -1.0
        
        # Store results
        machine_counts.append(i + 1)
        execution_times.append(global_time)
        seq_times.append(seq_time/(i + 1))

plt.figure(figsize=(10, 6))
plt.plot(machine_counts, execution_times, marker="o", linestyle="-", color="b", label="Parallel Execution Time")
plt.plot(machine_counts, seq_times, marker="o", linestyle="-", color="orange", label="Sequential Time")
plt.title(f"Global Execution Time vs Number of Machines ({test_suffix})")
plt.xlabel("Number of Machines")
plt.ylabel("Global Execution Time (seconds)")
plt.grid(True)
plt.xticks(machine_counts)
plt.legend(loc="upper right")  # Add legend to the upper-right corner
plt.tight_layout()

# Save the graph to a file
plt.savefig(graph_filename)
print(f"Graph saved as {graph_filename}.")

