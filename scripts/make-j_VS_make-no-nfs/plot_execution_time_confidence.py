#!/usr/bin/env python3
import os
import ast
import numpy as np
import matplotlib.pyplot as plt
import sys
from scipy import stats

def parse_args():
    """Parse and validate command-line arguments."""
    if len(sys.argv) != 2:
        print("Usage: python3 plot_execution_time.py <test_directory_suffix>")
        sys.exit(1)
    
    test_suffix = sys.argv[1]
    output_dir = f"output_logs_{test_suffix}"
    
    # Validate output directory exists
    if not os.path.exists(output_dir):
        print(f"Error: Directory {output_dir} does not exist.")
        sys.exit(1)
    
    return test_suffix, output_dir

def load_sequential_times(output_dir):
    """Load sequential times from multiple files."""
    seq_times = []
    
    # Find all sequential time files
    for filename in os.listdir(output_dir):
        if filename.startswith("seq_time_file_") and filename.endswith(".txt"):
            filepath = os.path.join(output_dir, filename)
            try:
                with open(filepath, "r") as seq_file:
                    seq_times.append(float(seq_file.read().strip()))
            except (IOError, ValueError) as e:
                print(f"Warning: Could not read {filepath}: {e}")
    
    if not seq_times:
        print("Error: No sequential time files found.")
        sys.exit(1)
    
    return seq_times

def collect_execution_times(output_dir):
    """Collect execution times for different machine counts."""
    machine_times = {}
    
    # Iterate through subdirectories
    for subdir in os.listdir(output_dir):
        if subdir.startswith("with_") and subdir.endswith("_machines"):
            # Extract number of machines
            num_machines = int(subdir.split("_")[1])
            machine_times[num_machines] = []
            
            # Full path to machine count subdirectory
            machine_dir = os.path.join(output_dir, subdir)
            
            # Find all times files for this machine count
            for filename in os.listdir(machine_dir):
                if filename.startswith("times_iteration_") and filename.endswith(".txt"):
                    filepath = os.path.join(machine_dir, filename)
                    try:
                        with open(filepath, "r") as times_file:
                            times_data = ast.literal_eval(times_file.read().strip())
                            
                            # Extract global execution time
                            global_time = times_data[0]
                            error_logs = times_data[-1]
                            
                            # Skip if there were errors
                            if error_logs != "[]":
                                global_time = -1.0
                            
                            machine_times[num_machines].append(global_time)
                    except (IOError, SyntaxError, ValueError) as e:
                        print(f"Warning: Could not process {filepath}: {e}")
    
    return machine_times

def plot_performance_with_confidence(test_suffix, output_dir):
    """Plot performance graph with confidence intervals."""
    # Load sequential times
    seq_times = load_sequential_times(output_dir)
    
    # Collect execution times
    machine_times = collect_execution_times(output_dir)
    
    # Prepare data for plotting
    machine_counts = sorted(machine_times.keys())
    execution_times = []
    execution_errors = []
    seq_times_normalized = []
    seq_time_errors = []
    
    # Calculate mean and confidence intervals for each machine count
    for machines in machine_counts:
        # Filter out invalid times (negative or zero)
        valid_times = [t for t in machine_times[machines] if t > 0]
        
        if not valid_times:
            print(f"Warning: No valid times for {machines} machines.")
            continue
        
        # Mean and 95% confidence interval for execution times
        mean_time = np.mean(valid_times)
        time_ci = stats.t.interval(confidence=0.95, df=len(valid_times)-1, 
                                   loc=np.mean(valid_times), 
                                   scale=stats.sem(valid_times))
        
        # Normalized sequential time
        mean_seq_time = np.mean(seq_times) / machines
        seq_time_ci = stats.t.interval(confidence=0.95, df=len(seq_times)-1, 
                                       loc=mean_seq_time, 
                                       scale=stats.sem(seq_times))
        
        execution_times.append(mean_time)
        execution_errors.append(mean_time - time_ci[0])
        seq_times_normalized.append(mean_seq_time)
        seq_time_errors.append(mean_seq_time - seq_time_ci[0])
    
    # Create the plot
    plt.figure(figsize=(12, 7), dpi=300)
    
    # Plot parallel execution times
    plt.errorbar(machine_counts, execution_times, 
                 yerr=execution_errors, 
                 fmt='o-', color='blue', 
                 capsize=5, 
                 label='Parallel Execution Time')
    
    # Plot normalized sequential times
    plt.errorbar(machine_counts, seq_times_normalized, 
                 yerr=seq_time_errors, 
                 fmt='o-', color='orange', 
                 capsize=5, 
                 label='Sequential Time (Normalized)')
    
    plt.title(f"Global Execution Time vs Number of Machines ({test_suffix})")
    plt.xlabel("Number of Machines")
    plt.ylabel("Global Execution Time (seconds)")
    plt.grid(True)
    plt.xticks(machine_counts)
    plt.legend(loc="upper right")
    plt.tight_layout()
    
    # Save the graph
    graph_filename = f"{output_dir}/perf_graph_{test_suffix}_with_ci.png"
    plt.savefig(graph_filename)
    print(f"Graph saved as {graph_filename}.")

def main():
    """Main function to orchestrate the performance plotting."""
    test_suffix, output_dir = parse_args()
    plot_performance_with_confidence(test_suffix, output_dir)

if __name__ == "__main__":
    main()