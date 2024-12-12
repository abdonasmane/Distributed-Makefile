#!/usr/bin/env python3
import os
import subprocess
from colorama import Fore, Style, init
import re
import sys


if len(sys.argv) != 2:
    print("Usage: python3 make_perf.py <test_directory_suffix [Ex: test6]>")
    sys.exit(1)

erro_pattern = r".*Fatal Error.*"
global_time_pattern = r"Global Time\s*:\s*([\d\.]+)"
parsing_time_pattern = r"Parsing Time\s*:\s*([\d\.]+)"
graph_time_pattern = r"Graph Build Time\s*:\s*([\d\.]+)"
spark_conf_time_pattern = r"Spark Configuration Time\s*:\s*([\d\.]+)"
execution_time_pattern = r"Execution Time\s*:\s*([\d\.]+)"


# Parameters
machines = [("paradoxe-32","rennes"),
            ("paradoxe-31","rennes"),
            ("paradoxe-30","rennes"),
            ("paradoxe-10","rennes"),
            ("paradoxe-2","rennes"),
            ("paradoxe-5","rennes"),
            ("paradoxe-27","rennes"),
            ("paradoxe-4","rennes"),
            ("paradoxe-3","rennes"),
            ("paradoxe-29","rennes"),
            ("paradoxe-28","rennes"),
            ("paradoxe-26","rennes"),
            ("paradoxe-19","rennes"),
            ("paradoxe-1","rennes")
            ]
results = []
master_ip = "172.16.101.32" # master node IP
test_directory_suffix = sys.argv[1] # test dir
base_path = "/home/anasmane/systemes-distribues"
spark_path = "/home/anasmane/spark-3.5.3-bin-hadoop3"
script_name = "./setup_spark_clusters.sh"
number_of_samples = 4 # take 4 mesures per test

# Ensure output directory exists
output_dir = f"output_logs_{test_directory_suffix}"
os.makedirs(output_dir, exist_ok=True)

for i, master_node_name in enumerate(machines):
    dir_name = f"with_{i+1}_machines"
    store_file_in = f"{output_dir}/{dir_name}"
    os.makedirs(store_file_in, exist_ok=True)
    # Create configX.txt
    config_filename = f"config{i+1}.txt"
    with open(f"{store_file_in}/{config_filename}", "w") as config_file:
        # Master configuration
        config_file.write(f"master_site_name={machines[0][1]}\n")
        config_file.write(f"master_node_name={machines[0][0]}\n")
        config_file.write(f"master_node_ip={master_ip}\n")
        config_file.write("master_node_port=3000\n")
        
        # Worker configuration
        for j in range(1, i+1):
            config_file.write(f"worker_site_name={machines[j][1]}\n")
            config_file.write(f"worker_node_name={machines[j][0]}\n")
    
    for k in range(1, number_of_samples+1):
        # Command to execute
        command = [
            script_name,
            f"{store_file_in}/{config_filename}",
            f"{base_path}/src/test/resources/{test_directory_suffix}/Makefile",
            base_path,
            spark_path,
            "all",
            "anasmane",
            "enable",
            "NFS",
            "NO_TMP",
        ]
        
        # Run command and save output
        try:
            print(Fore.YELLOW + f"Executing :\n{command} for the {k}th time with {i+1} machines")
            result = subprocess.run(command, capture_output=True, text=True, check=True)
            log_filename = f"{store_file_in}/log_iteration_{i+1}_{k}.txt"
            with open(log_filename, "w") as log_file:
                log_file.write(result.stdout)
            print(Fore.GREEN + f"Iteration {i}_{k}: Command executed successfully. Output saved in {log_filename}.")
            with open(log_filename, "r") as log_file:
                log_data = log_file.read()
            
            global_time = re.search(global_time_pattern, log_data)
            parsing_time = re.search(parsing_time_pattern, log_data)
            graph_time = re.search(graph_time_pattern, log_data)
            spark_conf_time = re.search(spark_conf_time_pattern, log_data)
            execution_time = re.search(execution_time_pattern, log_data)
            err_matches = re.findall(erro_pattern, log_data, re.MULTILINE)
            
            times = [
                float(global_time.group(1)) if global_time else -1.0,
                float(parsing_time.group(1)) if parsing_time else -1.0,
                float(graph_time.group(1)) if graph_time else -1.0,
                float(spark_conf_time.group(1)) if spark_conf_time else -1.0,
                float(execution_time.group(1)) if execution_time else -1.0,
                str(err_matches)
            ]
            
            # Save extracted times to a new file
            times_filename = f"{store_file_in}/times_iteration_{i+1}_{k}.txt"
            with open(times_filename, "w") as times_file:
                times_file.write(str(times))
            print(Fore.GREEN + f"Iteration {i}_{k}: Times extracted and saved in {times_filename}.")
        except subprocess.CalledProcessError as e:
            error_filename = f"{store_file_in}/error_iteration_{i+1}_{k}.txt"
            with open(error_filename, "w") as error_file:
                error_file.write(e.stderr)
            print(Fore.RED + f"Iteration {i}_{k}: Command failed. Error saved in {error_filename}.")
