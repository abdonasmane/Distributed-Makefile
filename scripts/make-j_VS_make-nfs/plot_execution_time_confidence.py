#!/usr/bin/env python3
import os
import ast
import numpy as np
import random
import matplotlib.pyplot as plt
import sys
from scipy import stats

random.seed(42)
# test9 matrix, all tasks are sleep5
SLEEP_MATRIX_1 = [[5 for _ in range(3952)]]
# test8 matrix
SLEEP_MATRIX_2 = [[random.randint(1, 10) for _  in range(random.randint(2, 2000))] for _ in range(4)]
# File containing the model coefficients
SPARK_LOSS_MODEL_COEFFICIENTS_FILE = "../spark_losses_estimation/model_coefficients.txt"


def parse_args():
    """Parse and validate command-line arguments."""
    if len(sys.argv) != 3:
        print("Usage: python3 plot_execution_time.py <test_directory_suffix> <sleep_test 0/1/2>\n"+
              "0 if not running sleep test\n" +
              "1 if running test9\n" +
              "2 if running test8\n")
        sys.exit(1)
    
    test_suffix = sys.argv[1]
    output_dir = f"output_logs_{test_suffix}"
    sleep_test = int(sys.argv[2])
    
    # Validate output directory exists
    if not os.path.exists(output_dir):
        print(f"Error: Directory {output_dir} does not exist.")
        sys.exit(1)
    
    return test_suffix, output_dir, sleep_test

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

def load_model_coefficients(file_path):
    """
    Load the model coefficients and intercept from the file.
    """
    with open(file_path, 'r') as f:
        lines = f.readlines()
        coefficients = eval(lines[0].split(":")[1].strip())  # Extract coefficients
        intercept = float(lines[1].split(":")[1].strip())   # Extract intercept
    return coefficients, intercept

def predict_graph_time(model_file, levels, targets):
    """
    Predict graph build time for the given number of levels and targets.
    """
    coefficients, intercept = load_model_coefficients(model_file)
    # Prediction formula: y = coefficients[0]*levels + coefficients[1]*targets + intercept
    prediction = np.dot([levels, targets], coefficients) + intercept
    return prediction

def theoric_make_nfs(number_of_cores, number_of_machines, sleep_matrix):
    s = 0
    for times in sleep_matrix:
        t_largest = max(times)
        sum_per_line = sum(times)
        s += (sum_per_line)/(number_of_cores*number_of_machines) + t_largest
    s += random.gauss(1.18, 0.1) # spark context
    # s += random.gauss(4.0, 0.2) # spark messages
    return s

def plot_performance_with_confidence(test_suffix, output_dir, sleep_test):
    """Plot performance graph with confidence intervals."""
    # Load sequential times
    seq_times = load_sequential_times(output_dir)
    
    # Collect execution times
    machine_times = collect_execution_times(output_dir)
    
    # Prepare data for plotting
    machine_counts = sorted(machine_times.keys())
    theoric_times = []
    if sleep_test:
        number_of_cores_per_machine = 104
        if sleep_test == 1:
            theoric_times = [theoric_make_nfs(number_of_cores_per_machine, x, SLEEP_MATRIX_1) for x in machine_counts]
            spark_loss_time = predict_graph_time(SPARK_LOSS_MODEL_COEFFICIENTS_FILE, len((SLEEP_MATRIX_1)), max([len(line) for line in SLEEP_MATRIX_1]))
            theoric_times = [x + spark_loss_time for x in theoric_times]
        # elif sleep_test == 2:
        #     theoric_times = [theoric_make_nfs(number_of_cores_per_machine, x, SLEEP_MATRIX_2) for x in machine_counts]
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
    
    if sleep_test:
        plt.plot(machine_counts, theoric_times, 'o-', color='red', label='Theoric Model')
    
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
    test_suffix, output_dir, p_sleep_test = parse_args()
    plot_performance_with_confidence(test_suffix, output_dir, p_sleep_test)

if __name__ == "__main__":
    main()