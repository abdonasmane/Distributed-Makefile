#!/usr/bin/env python3
import os
import subprocess
from colorama import Fore, Style, init
import re
import sys

# Parameters
machine = ("paradoxe-3","rennes")

max_level = 10
max_targets = 10

def launch_tests(username, master_ip, test_directory_suffix, base_path, spark_path, nfs_mode, tmp_mode, number_of_samples):
    # Ensure output directory exists
    output_dir = f"graph_build_estimation"
    os.makedirs(output_dir, exist_ok=True)
    matrix = [[0]*max_targets for _ in range(max_level)]
    for i in range(max_level):
        for j in range(max_targets):
            current_levels = i+1
            current_max_targets = j+1
            # create Makefile
            with open(f"{output_dir}/Makefile", "w") as makefile:
                all_targets = [f"list{z}_{x}" for z in range(1, current_levels + 1) for x in range(1, current_max_targets + 1)]
                makefile.write("all:\t" + " ".join(all_targets) + "\n")
                for k in range(1, current_levels + 1):
                    for i in range(1, targets_per_level + 1):
                        start = random.randint(2, interval_length - 100)
                        end = random.randint(start+1, interval_length)
                        if level == 1:
                            # Level 1 files: No dependencies, simple range calculation
                            f.write(f"list{level}_{i}:\tpremiera\n")
                            f.write(f"\t./premier {start} {end} > list{level}_{i}.txt\n")
                        else:
                            # Higher-level files depend on all previous levels
                            dependencies = " ".join([f"list{level-1}_{j}" for j in random.sample(range(1, targets_per_level + 1), min(20, targets_per_level-1))])
                            f.write(f"list{level}_{i}:\tpremiera {dependencies}\n")
                            f.write(f"\t./premier {start} {end} >> list{level}_{i}.txt\n")
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
    if len(sys.argv) != 8:
        print("Usage: python3 make_perf.py <username> <master_ip> <test_directory_suffix: eg. test5> "+
              "<base_path> <nfs_mode: NFS/NO_NFS> <tmp_mode: TMP/NO_TMP> "+
              "<number_of_samples_per_test>")
        sys.exit(1)
    
    p_username = sys.argv[1]
    p_master_ip = sys.argv[2]
    p_test_directory_suffix = sys.argv[3]
    p_base_path = sys.argv[4]
    p_spark_path = f"/home/{p_username}/spark-3.5.3-bin-hadoop3"
    p_nfs_mode = sys.argv[5] 
    p_tmp_mode = sys.argv[6] 
    p_number_of_samples = int(sys.argv[7])

    launch_tests(p_username, p_master_ip, p_test_directory_suffix, p_base_path, p_spark_path, p_nfs_mode, p_tmp_mode, p_number_of_samples)
    print(Fore.GREEN + "\n\nTests Done")