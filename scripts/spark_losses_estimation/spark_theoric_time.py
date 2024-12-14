#!/usr/bin/env python3
import re
import sys
import subprocess
import numpy as np
from colorama import Fore
from sklearn.linear_model import LinearRegression
import matplotlib.pyplot as plt

# This script is supposed to run on paradoxe
# And it supposed that someone manually do spark master and worker setup ;)

GENERATE_MAKEFILE_SCRIPT = "./generate_makefile.py"
RUN_SPARK_PROGRAM = [
    "spark-submit",
    "--driver-memory", "50G",
    "--executor-memory", "50G",
    "--conf", "spark.executor.extraJavaOptions=-XX:-UseGCOverheadLimit",
    "--conf", "spark.driver.extraJavaOptions=-XX:-UseGCOverheadLimit",
    "--class", "Main",
    "distributed-make-project-1.0.jar",
    "Makefile",
    "all",
    "spark://172.16.101.10:3000",
    "NO_NFS"
]

EXECUTION_TIME_PATTERN = r"Execution Time\s*:\s*([\d\.]+)"
OUTPUT_FILE = "matrix_output.txt"
MODEL_COEFFICIENTS_FILE = "model_coefficients.txt"
PLOT_FILE = "spark_time_plot.png"

def launch_tests(max_level, max_targets, targets_step):
    """
    Launching spark on an empty makefile and saving results in a file.
    """
    # Ensure output directory exists
    matrix = [[0 for _ in range(int(max_targets/targets_step))] for _ in range(max_level)]
    for i in range(max_level):
        for j in range(int(max_targets/targets_step)):
            current_levels = i+1
            current_max_targets = (j+1)*targets_step
            # create Makefile
            generate_makefile = [
                GENERATE_MAKEFILE_SCRIPT,
                str(current_levels),
                str(current_max_targets)
            ]
            try:
                print(Fore.YELLOW + f"Proccessing {current_levels} levels & {current_max_targets} targets")
                subprocess.run(generate_makefile, capture_output=True, text=True, check=True)
                result = subprocess.run(RUN_SPARK_PROGRAM, capture_output=True, text=True, check=True)
                
                log_data = result.stdout
                spark_time = re.search(EXECUTION_TIME_PATTERN, log_data)
                matrix[i][j] = float(spark_time.group(1)) if spark_time else -1.0
            except subprocess.CalledProcessError:
                print(Fore.RED + f"{current_levels} levels & {current_max_targets} targets : FAILED")
                matrix[i][j] = -1.0
        if i == 0:
            with open(OUTPUT_FILE, 'w') as file:
                file.write(f"Level {current_levels}\n"+','.join(map(str, matrix[i])) + '\n\n\n')
        else:
            with open(OUTPUT_FILE, 'a') as file:
                file.write(f"Level {current_levels}\n"+','.join(map(str, matrix[i])) + '\n\n\n')
    return matrix

def train_and_plot_model(matrix, max_level, max_targets, targets_step):
    """
    Train a linear regression model and plot results.
    """
    # Prepare data for regression model
    X, y = [], []
    for i in range(max_level):
        for j in range(int(max_targets / targets_step)):
            if matrix[i][j] != -1.0:  # Use valid data points only
                X.append([i + 1, (j + 1) * targets_step])  # Features: levels, targets
                y.append(matrix[i][j])  # Target: graph time

    X = np.array(X)
    y = np.array(y)

    # Train a Linear Regression model
    model = LinearRegression()
    model.fit(X, y)

    # Save the coefficients and intercept
    with open(MODEL_COEFFICIENTS_FILE, 'w') as f:
        f.write(f"Coefficients: {model.coef_.tolist()}\n")
        f.write(f"Intercept: {model.intercept_}\n")
    print(Fore.GREEN + f"Model coefficients saved to {MODEL_COEFFICIENTS_FILE}")

    # Generate predictions for plotting
    levels = np.arange(1, max_level + 1)
    targets = np.arange(targets_step, max_targets + 1, targets_step)
    levels, targets = np.meshgrid(levels, targets)
    levels_flat, targets_flat = levels.ravel(), targets.ravel()
    predictions = model.predict(np.column_stack((levels_flat, targets_flat)))

    # Reshape predictions for plotting
    predictions = predictions.reshape(levels.shape)

    # Plot the results
    fig = plt.figure()
    ax = fig.add_subplot(111, projection='3d')
    ax.plot_surface(levels, targets, predictions, cmap="viridis", alpha=0.8)
    ax.set_title("Graph Build Time Prediction")
    ax.set_xlabel("Levels")
    ax.set_ylabel("Targets")
    ax.set_zlabel("Graph Build Time")
    plt.savefig(PLOT_FILE)
    print(Fore.GREEN + f"Plot saved to {PLOT_FILE}")
    plt.show()

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python3 spark_theoric_perf.py max_levels max_targets targets_step")
        sys.exit(1)
    
    try:
        p_max_levels = int(sys.argv[1])
        p_max_targets = int(sys.argv[2])
        p_targets_step = int(sys.argv[3])

        matrix_data = launch_tests(p_max_levels, p_max_targets, p_targets_step)
        train_and_plot_model(matrix_data, p_max_levels, p_max_targets, p_targets_step)
        print(Fore.GREEN + "\n\Spark time Tests and Analysis Done")
    except ValueError:
        print(Fore.RED + "Error: All arguments must be integers")
        sys.exit(1)
