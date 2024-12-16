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
        print("Usage: python3 compare_graphs.py <test_directory_suffix>")
        sys.exit(1)

    test_suffix = sys.argv[1]

    # Validate both directories exist
    base_dirs = ["make-j_VS_make-nfs", "make-j_VS_make-no-nfs"]
    for base_dir in base_dirs:
        output_dir = os.path.join(base_dir, f"output_logs_{test_suffix}")
        if not os.path.exists(output_dir):
            print(f"Error: Directory {output_dir} does not exist.")
            sys.exit(1)

    return test_suffix, base_dirs

def load_sequential_times(output_dir):
    """Load sequential times from multiple files."""
    seq_times = []

    for filename in os.listdir(output_dir):
        if filename.startswith("seq_time_file_") and filename.endswith(".txt"):
            filepath = os.path.join(output_dir, filename)
            try:
                with open(filepath, "r") as seq_file:
                    seq_times.append(float(seq_file.read().strip()))
            except (IOError, ValueError) as e:
                print(f"Warning: Could not read {filepath}: {e}")

    if not seq_times:
        print(f"Error: No sequential time files found in {output_dir}.")
        sys.exit(1)

    return seq_times

def collect_parallel_times(output_dir):
    """Collect parallel execution times for different machine counts."""
    machine_times = {}

    for subdir in os.listdir(output_dir):
        if subdir.startswith("with_") and subdir.endswith("_machines"):
            num_machines = int(subdir.split("_")[1])
            machine_times[num_machines] = []

            machine_dir = os.path.join(output_dir, subdir)

            for filename in os.listdir(machine_dir):
                if filename.startswith("times_iteration_") and filename.endswith(".txt"):
                    filepath = os.path.join(machine_dir, filename)
                    try:
                        with open(filepath, "r") as times_file:
                            times_data = ast.literal_eval(times_file.read().strip())
                            global_time = times_data[0]
                            error_logs = times_data[-1]

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

    mean_seq_time = np.mean(seq_times)

    for num_machines in machine_counts:
        valid_times = [t for t in machine_times[num_machines] if t > 0]

        if not valid_times:
            print(f"Warning: No valid times for {num_machines} machines.")
            continue

        measured_acc = [mean_seq_time / par_time for par_time in valid_times]
        measured_eff = [ma / num_machines for ma in measured_acc]

        perfect_acc = [num_machines for _ in valid_times]
        perfect_eff = [1.0 for _ in valid_times]

        def calculate_ci(data):
            mean = np.mean(data)
            ci = stats.t.interval(alpha=0.95, df=len(data)-1, 
                                  loc=mean, 
                                  scale=stats.sem(data))
            return mean, mean - ci[0]

        metrics['measured_acceleration'].append(np.mean(measured_acc))
        metrics['measured_efficiency'].append(np.mean(measured_eff))
        metrics['perfect_acceleration'].append(np.mean(perfect_acc))
        metrics['perfect_efficiency'].append(np.mean(perfect_eff))
        metrics['acceleration_errors'].append(calculate_ci(measured_acc)[1])
        metrics['efficiency_errors'].append(calculate_ci(measured_eff)[1])

    return machine_counts, metrics

def plot_comparison_graphs(test_suffix, base_dirs, machine_counts, metrics_data, plot_type):
    """Plot comparison graphs for acceleration and efficiency."""
    plt.figure(figsize=(12, 7), dpi=300)

    colors = ["blue", "green"]
    labels = ["NFS", "No-NFS"]

    for i, (base_dir, metrics) in enumerate(metrics_data):
        plt.errorbar(machine_counts[i], 
                     metrics[f'measured_{plot_type}'], 
                     yerr=metrics[f'{plot_type}_errors'], 
                     fmt='o-', 
                     color=colors[i], 
                     capsize=5, 
                     label=f'{labels[i]} Measured {plot_type.capitalize()}')

    plt.plot(machine_counts[i], 
                metrics[f'perfect_{plot_type}'], 
                linestyle='--', 
                color=colors[i], 
                label=f'Perfect {plot_type.capitalize()}')

    plt.title(f"Comparison of {plot_type.capitalize()} vs Number of Machines ({test_suffix})")
    plt.xlabel("Number of Machines")
    ylabel = "Acceleration (Seq Time / Parallel Time)" if plot_type == "acceleration" else "Efficiency (Measured Acceleration / Machines)"
    plt.ylabel(ylabel)
    plt.grid(True)
    if plot_type == "acceleration":
        plt.legend(loc="upper left")
    else:
        plt.legend(loc="upper right")
    plt.tight_layout()

    graph_filename = f"comparison_{plot_type}_{test_suffix}_with_ci.png"
    plt.savefig(graph_filename)
    print(f"{plot_type.capitalize()} comparison graph saved as {graph_filename}.")

def main():
    """Main function to generate comparison graphs."""
    test_suffix, base_dirs = parse_args()

    metrics_data = []
    machine_counts = []

    for base_dir in base_dirs:
        output_dir = os.path.join(base_dir, f"output_logs_{test_suffix}")

        seq_times = load_sequential_times(output_dir)
        machine_times = collect_parallel_times(output_dir)
        machine_count, metrics = calculate_metrics(seq_times, machine_times)

        machine_counts.append(machine_count)
        metrics_data.append((base_dir, metrics))

    # Plot acceleration and efficiency comparison
    plot_comparison_graphs(test_suffix, base_dirs, machine_counts, metrics_data, 'acceleration')
    plot_comparison_graphs(test_suffix, base_dirs, machine_counts, metrics_data, 'efficiency')

if __name__ == "__main__":
    main()
