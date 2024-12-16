#!/usr/bin/env python3
import os
import sys
import ast
import numpy as np
import matplotlib.pyplot as plt
from scipy import stats

def parse_args():
    """Parse and validate command-line arguments."""
    if len(sys.argv) != 2:
        print("Usage: python3 plot_acc_eff.py <test_directory_suffix>")
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

def collect_parallel_times(output_dir):
    """Collect parallel execution times for different machine counts."""
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

def calculate_metrics(seq_times, machine_times):
    """Calculate acceleration and efficiency metrics with confidence intervals."""
    machine_counts = sorted(machine_times.keys())
    metrics = {
        'measured_acceleration': [],
        'perfect_acceleration': [],
        'measured_efficiency': [],
        'perfect_efficiency': [],
        'acceleration_errors': [],
        'efficiency_errors': [],
    }

    # Mean sequential time
    mean_seq_time = np.mean(seq_times)

    for num_machines in machine_counts:
        # Filter out invalid times
        valid_times = [t for t in machine_times[num_machines] if t > 0]
        
        if not valid_times:
            print(f"Warning: No valid times for {num_machines} machines.")
            continue
        
        # Measured acceleration and efficiency
        measured_acc = [mean_seq_time / par_time for par_time in valid_times]
        measured_eff = [ma / num_machines for ma in measured_acc]
        
        # Perfect acceleration calculation (sequential time / (num_machines * parallel_time))
        perfect_acc = [num_machines for _ in valid_times]
        perfect_eff = [perf_acc/num_machines for perf_acc in perfect_acc]
        
        # Calculate mean and confidence intervals
        def calculate_ci(data):
            mean = np.mean(data)
            ci = stats.t.interval(alpha=0.95, df=len(data)-1, 
                                  loc=mean, 
                                  scale=stats.sem(data))
            return mean, mean - ci[0]
        
        # Store metrics
        metrics['measured_acceleration'].append(np.mean(measured_acc))
        metrics['measured_efficiency'].append(np.mean(measured_eff))
        metrics['perfect_acceleration'].append(np.mean(perfect_acc))
        metrics['perfect_efficiency'].append(np.mean(perfect_eff))
        
        # Calculate and store confidence intervals
        metrics['acceleration_errors'].append(
            calculate_ci(measured_acc)[1]
        )
        metrics['efficiency_errors'].append(
            calculate_ci(measured_eff)[1]
        )

    return machine_counts, metrics

def plot_performance_metrics(test_suffix, output_dir, machine_counts, metrics, plot_type):
    """Plot acceleration or efficiency with error bars."""
    plt.figure(figsize=(12, 7), dpi=300)
    
    # Plot measured and perfect metrics
    plt.errorbar(machine_counts, 
                 metrics[f'measured_{plot_type}'], 
                 yerr=metrics[f'{plot_type}_errors'], 
                 fmt='o-', color='blue', 
                 capsize=5, 
                 label=f'Measured {plot_type.capitalize()}')
    
    plt.errorbar(machine_counts, 
                 metrics[f'perfect_{plot_type}'], 
                 fmt='o-', color='orange', 
                 capsize=5, 
                 label=f'Perfect {plot_type.capitalize()}')
    
    plt.title(f"{plot_type.capitalize()} vs Number of Machines ({test_suffix})")
    plt.xlabel("Number of Machines")
    if plot_type == "acceleration":
        label = f"{plot_type.capitalize()} (Seq Time / Parallel Time)"
    else:
        label = f"{plot_type.capitalize()} (Seq Time / (Machines × Parallel Time))"
    plt.ylabel(label)
    plt.grid(True)
    plt.xticks(machine_counts)
    plt.legend(loc="upper left")
    plt.tight_layout()
    
    # Save the graph
    graph_filename = f"{output_dir}/{plot_type}_{test_suffix}_with_ci.png"
    plt.savefig(graph_filename)
    print(f"{plot_type.capitalize()} graph saved as {graph_filename}.")

def main():
    """Main function to orchestrate the acceleration and efficiency plotting."""
    # Parse arguments and set up directories
    test_suffix, output_dir = parse_args()
    
    # Load sequential times from multiple files
    seq_times = load_sequential_times(output_dir)
    
    # Collect parallel execution times
    machine_times = collect_parallel_times(output_dir)
    
    # Calculate acceleration and efficiency metrics
    machine_counts, metrics = calculate_metrics(seq_times, machine_times)
    
    # Plot acceleration
    plot_performance_metrics(test_suffix, output_dir, machine_counts, metrics, 'acceleration')
    
    # Plot efficiency
    plot_performance_metrics(test_suffix, output_dir, machine_counts, metrics, 'efficiency')

if __name__ == "__main__":
    main()