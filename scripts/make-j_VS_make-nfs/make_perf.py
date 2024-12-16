#!/usr/bin/env python3
import os
import subprocess
from colorama import Fore, Style, init
import re
import sys

erro_pattern = r".*Fatal Error.*"
global_time_pattern = r"Global Time\s*:\s*([\d\.]+)"
parsing_time_pattern = r"Parsing Time\s*:\s*([\d\.]+)"
graph_time_pattern = r"Graph Build Time\s*:\s*([\d\.]+)"
spark_conf_time_pattern = r"Spark Configuration Time\s*:\s*([\d\.]+)"
execution_time_pattern = r"Execution Time\s*:\s*([\d\.]+)"

with open("config.txt", "r") as file:
    config_text = file.read()

master_pattern = r"master_site_name=(\w+)\s+master_node_name=(paradoxe-\d+)"
worker_pattern = r"worker_site_name=(\w+)\s+worker_node_name=(paradoxe-\d+)"
ip_pattern = r"master_node_ip=(\d+\.\d+\.\d+\.\d+)"

# Extract the master node
master_match = re.search(master_pattern, config_text)
master_node = (master_match.group(2), master_match.group(1)) if master_match else None

# Extract worker nodes
worker_matches = re.findall(worker_pattern, config_text)
for i in range(len(worker_matches)):
    worker_matches[i] = (worker_matches[i][1], worker_matches[i][0])

# Combine master and worker nodes
machines = [master_node] + worker_matches if master_node else worker_matches

master_ip = re.findall(ip_pattern, config_text)[0]
print(Fore.GREEN + f"Machines : ")
print(Fore.YELLOW + f"\tMaster -> [{machines[0][0]}, {machines[0][1]}]")
print(Fore.YELLOW + f"\tWorkers ->")
for i in range(1, len(machines)):
    print(Fore.YELLOW + f"\t\t[{machines[i][0]}, {machines[i][1]}]")
print("")
print(Fore.GREEN + f"Master IP : {master_ip}")
print("")

script_name = "./setup_spark_clusters.sh"
clean_script = "./clean_after_iteration.sh"
after_setup_script = "./run_after_iteration.sh"
kill_all_machines = "./stop-all-spark.sh"

def launch_tests(username, test_directory_suffix, base_path, spark_path, nfs_mode, tmp_mode, number_of_samples):
    # Ensure output directory exists
    kill_machines = [
        kill_all_machines,
        username,
        "kill_machines.txt",
    ]
    try:
        print(Fore.YELLOW + f"Executing :\n{kill_machines} before launching tests")
        result = subprocess.run(kill_machines, capture_output=True, text=True, check=True)
        print(Fore.GREEN + "Killed all machines Successfully")
    except subprocess.CalledProcessError as e:
        print(Fore.RED + "Failed to kill machines")
        print(Fore.RED + " Error : " + e.stderr)

    output_dir = f"output_logs_{test_directory_suffix}"
    os.makedirs(output_dir, exist_ok=True)
    for i in range(1, len(machines)+1):
        dir_name = f"with_{i}_machines"
        store_file_in = f"{output_dir}/{dir_name}"
        os.makedirs(store_file_in, exist_ok=True)
        # Create configX.txt
        config_filename = f"config{i}.txt"
        with open(f"{store_file_in}/{config_filename}", "w") as config_file:
            # Master configuration
            config_file.write(f"master_site_name={machines[0][1]}\n")
            config_file.write(f"master_node_name={machines[0][0]}\n")
            config_file.write(f"master_node_ip={master_ip}\n")
            config_file.write("master_node_port=3000\n")
            
            # Worker configuration
            for j in range(1, i):
                config_file.write(f"worker_site_name={machines[j][1]}\n")
                config_file.write(f"worker_node_name={machines[j][0]}\n")
        
        for k in range(1, number_of_samples+1):
            if k > 1:
                clean_command = [
                    clean_script,
                    username,
                    f"{store_file_in}/{config_filename}",
                    f"{base_path}/src/test/resources/{test_directory_suffix}/Makefile",
                    nfs_mode,
                    tmp_mode
                ]
                try:
                    print(Fore.YELLOW + f"Executing :\n{clean_command} for the {k}th time with {i} machines")
                    result = subprocess.run(clean_command, capture_output=True, text=True, check=True)
                    print(Fore.GREEN + f"Cleaned Successfully in iteration {i}_{k}")
                except subprocess.CalledProcessError as e:
                    print(Fore.RED + f"Clean Failed in iteration {i}_{k}")
                    print(Fore.RED + " Error : " + e.stderr)
            # Command to execute
            command = []
            if k == 1:
                command = [
                    script_name,
                    f"{store_file_in}/{config_filename}",
                    f"{base_path}/src/test/resources/{test_directory_suffix}/Makefile",
                    base_path,
                    spark_path,
                    "all",
                    username,
                    nfs_mode,
                    tmp_mode,
                ]
            else:
                command = [
                    after_setup_script,
                    f"{store_file_in}/{config_filename}",
                    f"{base_path}/src/test/resources/{test_directory_suffix}/Makefile",
                    base_path,
                    spark_path,
                    "all",
                    username,
                    nfs_mode
                ]
            
            # Run command and save output
            try:
                print(Fore.YELLOW + f"Executing :\n{command} for the {k}th time with {i} machines")
                result = subprocess.run(command, capture_output=True, text=True, check=True)
                log_filename = f"{store_file_in}/log_iteration_{i}_{k}.txt"
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
                times_filename = f"{store_file_in}/times_iteration_{i}_{k}.txt"
                with open(times_filename, "w") as times_file:
                    times_file.write(str(times))
                print(Fore.GREEN + f"Iteration {i}_{k}: Times extracted and saved in {times_filename}.")
            except subprocess.CalledProcessError as e:
                error_filename = f"{store_file_in}/error_iteration_{i}_{k}.txt"
                with open(error_filename, "w") as error_file:
                    error_file.write(e.stderr)
                print(Fore.RED + f"Iteration {i}_{k}: Command failed. Error saved in {error_filename}.")

if __name__ == "__main__":
    if len(sys.argv) != 7:
        print("Usage: python3 make_perf.py <username> <test_directory_suffix: eg. test5> "+
              "<base_path> <nfs_mode: NFS/NO_NFS> <tmp_mode: TMP/NO_TMP> "+
              "<number_of_samples_per_test>")
        sys.exit(1)
    
    p_username = sys.argv[1]
    p_test_directory_suffix = sys.argv[2]
    p_base_path = sys.argv[3]
    p_spark_path = f"/home/{p_username}/spark-3.5.3-bin-hadoop3"
    p_nfs_mode = sys.argv[4] 
    p_tmp_mode = sys.argv[5] 
    p_number_of_samples = int(sys.argv[6])

    launch_tests(p_username, p_test_directory_suffix, p_base_path, p_spark_path, p_nfs_mode, p_tmp_mode, p_number_of_samples)
    print(Fore.GREEN + "\n\nTests Done")