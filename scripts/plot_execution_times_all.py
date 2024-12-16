#!/usr/bin/env python3

import os
import sys
import matplotlib.pyplot as plt
import numpy as np
from scipy import stats
import sys

####################################




#!/usr/bin/env python3
import os
import ast
import numpy as np
import random
import matplotlib.pyplot as plt
import sys
from scipy import stats

random.seed(42)

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
    return s

####################################

# Directories to compare
NFS_DIR = "make-j_VS_make-nfs"
NO_NFS_DIR = "make-j_VS_make-no-nfs"

# Model coefficient files for predictions
MODEL_COEFFICIENTS_FILE = "graph_build_estimation/model_coefficients.txt"
SPARK_LOSS_MODEL_COEFFICIENTS_FILE = "spark_losses_estimation/model_coefficients.txt"


def plot_combined_graph(test_suffix, sleep_test):
    """
    Generate a combined graph for a specific test suffix comparing the results
    from NFS and No-NFS directories.
    """
    # Paths to the output directories
    nfs_output_dir = os.path.join(NFS_DIR, f"output_logs_{test_suffix}")
    no_nfs_output_dir = os.path.join(NO_NFS_DIR, f"output_logs_{test_suffix}")

    # Validate directories exist
    if not os.path.exists(nfs_output_dir) or not os.path.exists(no_nfs_output_dir):
        print(f"Error: Missing directories for test suffix {test_suffix}.")
        sys.exit(1)

    # Load data for both directories
    nfs_seq_times = load_sequential_times(nfs_output_dir)
    no_nfs_seq_times = load_sequential_times(no_nfs_output_dir)

    nfs_machine_times = collect_execution_times(nfs_output_dir)
    no_nfs_machine_times = collect_execution_times(no_nfs_output_dir)

    # Get sorted machine counts
    nfs_machine_counts = sorted(nfs_machine_times.keys())
    no_nfs_machine_counts = sorted(no_nfs_machine_times.keys())

    # Prepare theoretical times if needed
    theoric_times_nfs, theoric_times_no_nfs = [], []
    if sleep_test:
        number_of_cores_per_machine = 104
        matrix = [[5 for _ in range(3952)]] if sleep_test == 1 else [
            [np.random.randint(1, 10) for _ in range(np.random.randint(2, 2000))]
            for _ in range(4)
        ]
        for x in nfs_machine_counts:
            theoric_time = theoric_make_nfs(number_of_cores_per_machine, x, matrix)
            # graph_build_time = predict_graph_time(
            #     MODEL_COEFFICIENTS_FILE, len(matrix), max([len(line) for line in matrix])
            # )
            spark_loss_time = predict_graph_time(
                SPARK_LOSS_MODEL_COEFFICIENTS_FILE, len(matrix), max([len(line) for line in matrix])
            )
            theoric_times_nfs.append(theoric_time + spark_loss_time)

        for x in no_nfs_machine_counts:
            theoric_time = theoric_make_nfs(number_of_cores_per_machine, x, matrix)
            graph_build_time = predict_graph_time(
                MODEL_COEFFICIENTS_FILE, len(matrix), max([len(line) for line in matrix])
            )
            spark_loss_time = predict_graph_time(
                SPARK_LOSS_MODEL_COEFFICIENTS_FILE, len(matrix), max([len(line) for line in matrix])
            )
            theoric_times_no_nfs.append(theoric_time + spark_loss_time + graph_build_time)

    # Calculate mean and confidence intervals
    def calculate_execution_times(machine_counts, machine_times, seq_times):
        execution_times, execution_errors = [], []
        seq_times_normalized, seq_time_errors = [], []

        for machines in machine_counts:
            valid_times = [t for t in machine_times[machines] if t > 0]
            if not valid_times:
                print(f"Warning: No valid times for {machines} machines.")
                continue

            mean_time = np.mean(valid_times)
            time_ci = stats.t.interval(
                alpha=0.95, df=len(valid_times) - 1, loc=mean_time, scale=stats.sem(valid_times)
            )

            mean_seq_time = np.mean(seq_times) / machines
            seq_time_ci = stats.t.interval(
                alpha=0.95, df=len(seq_times) - 1, loc=mean_seq_time, scale=stats.sem(seq_times)
            )

            execution_times.append(mean_time)
            execution_errors.append(mean_time - time_ci[0])
            seq_times_normalized.append(mean_seq_time)
            seq_time_errors.append(mean_seq_time - seq_time_ci[0])

        return execution_times, execution_errors, seq_times_normalized, seq_time_errors

    nfs_execution_times, nfs_execution_errors, nfs_seq_times_normalized, nfs_seq_time_errors = calculate_execution_times(
        nfs_machine_counts, nfs_machine_times, nfs_seq_times
    )
    no_nfs_execution_times, no_nfs_execution_errors, no_nfs_seq_times_normalized, no_nfs_seq_time_errors = calculate_execution_times(
        no_nfs_machine_counts, no_nfs_machine_times, no_nfs_seq_times
    )

    # Plot combined graph
    plt.figure(figsize=(12, 7), dpi=300)

    # NFS execution times
    plt.errorbar(
        nfs_machine_counts,
        nfs_execution_times,
        yerr=nfs_execution_errors,
        fmt="o-",
        color="blue",
        capsize=5,
        label="NFS Parallel Execution Time",
    )

    # No-NFS execution times
    plt.errorbar(
        no_nfs_machine_counts,
        no_nfs_execution_times,
        yerr=no_nfs_execution_errors,
        fmt="o-",
        color="green",
        capsize=5,
        label="No-NFS Parallel Execution Time",
    )

    plt.errorbar(no_nfs_machine_counts, no_nfs_seq_times_normalized, 
                 yerr=no_nfs_seq_time_errors, 
                 fmt='o-', color='purple', 
                 capsize=5, 
                 label='Sequential Time (Normalized)')
    
    # Theoretical times (if sleep test)
    if sleep_test:
        plt.plot(
            nfs_machine_counts, theoric_times_nfs, "o-", color="red", label="NFS Theoric Model"
        )
        plt.plot(
            no_nfs_machine_counts, theoric_times_no_nfs, "o-", color="orange", label="No-NFS Theoric Model"
        )

    plt.title(f"Global Execution Time vs Number of Machines ({test_suffix})")
    plt.xlabel("Number of Machines")
    plt.ylabel("Global Execution Time (seconds)")
    plt.grid(True)
    plt.xticks(nfs_machine_counts)
    plt.legend(loc="upper right")
    plt.tight_layout()

    # Save the graph
    graph_filename = f"combined_perf_graph_{test_suffix}_with_ci.png"
    plt.savefig(graph_filename)
    print(f"Combined graph saved as {graph_filename}.")


def main():
    if len(sys.argv) != 3:
        print("Usage: python3 plot_execution_time.py <test_directory_suffix> <sleep_test 0/1/2>\n"+
        "0 if not running sleep test\n" +
        "1 if running test9\n" +
        "2 if running test8\n")
        sys.exit(1)

    test_suffix = sys.argv[1]
    sleep_test = int(sys.argv[2])

    plot_combined_graph(test_suffix, sleep_test)


if __name__ == "__main__":
    main()
