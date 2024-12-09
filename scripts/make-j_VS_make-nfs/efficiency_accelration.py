#!/usr/bin/env python3
import os
import sys
import ast
import matplotlib.pyplot as plt

# Ensure the test directory and sequential time file are passed as arguments
if len(sys.argv) != 2:
    print("Usage: python3 plot_acc_eff.py <test_directory_suffix>")
    sys.exit(1)

# Parameters
test_suffix = sys.argv[1] 
output_dir = f"output_logs_{test_suffix}" 
seq_time_file = f"{output_dir}/seq_time_file.txt"
graph_acc_filename = f"{output_dir}/acceleration_{test_suffix}.png"
graph_eff_filename = f"{output_dir}/efficiency_{test_suffix}.png"

# Check if the directory and sequential time file exist
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
parallel_times = []
accelerations = []
efficiencies = []

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
        parallel_times.append(global_time)

# Compute acceleration and efficiency
for num_machines, par_time in zip(machine_counts, parallel_times):
    acceleration = seq_time / par_time if par_time > 0 else 0.0
    efficiency = seq_time / (num_machines * par_time) if par_time > 0 else 0.0
    accelerations.append(acceleration)
    efficiencies.append(efficiency)

# Plot acceleration
plt.figure(figsize=(10, 6))
plt.plot(machine_counts, accelerations, marker="o", linestyle="-", color="b")
plt.title(f"Acceleration vs Number of Machines ({test_suffix})")
plt.xlabel("Number of Machines")
plt.ylabel("Acceleration (Seq Time / Parallel Time)")
plt.grid(True)
plt.xticks(machine_counts)  # Ensure all machine counts are shown on the x-axis
plt.tight_layout()
plt.savefig(graph_acc_filename)
print(f"Acceleration graph saved as {graph_acc_filename}.")

# Plot efficiency
plt.figure(figsize=(10, 6))
plt.plot(machine_counts, efficiencies, marker="o", linestyle="-", color="g")
plt.title(f"Efficiency vs Number of Machines ({test_suffix})")
plt.xlabel("Number of Machines")
plt.ylabel("Efficiency (Seq Time / (Machines × Parallel Time))")
plt.grid(True)
plt.xticks(machine_counts)  # Ensure all machine counts are shown on the x-axis
plt.tight_layout()
plt.savefig(graph_eff_filename)
print(f"Efficiency graph saved as {graph_eff_filename}.")
