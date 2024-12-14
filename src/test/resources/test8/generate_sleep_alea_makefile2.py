#!/usr/bin/env python3
import random
import sys
from collections import defaultdict
random.seed(42)

def generate_sleep_alea_makefile2(num_levels, targets_per_level):
    sleep_matrix_2 = [[random.randint(1, 10) for _  in range(random.randint(2, targets_per_level))] for _ in range(num_levels)]
    with open('Makefile', 'w') as f:
        all_targets = [f"sleep{num_levels}_{i}" for i in range(1, len(sleep_matrix_2[-1]) + 1)]
        f.write("all: " + " ".join(all_targets) + "\n")
        
        # Generate rules for each level
        for level in range(1, num_levels + 1):
            for i in range(1, len(sleep_matrix_2[level-1]) + 1):
                if level == 1:
                    f.write(f"sleep{level}_{i}:\n")
                    f.write(f"\tsleep {sleep_matrix_2[level-1][i-1]}\n")
                else:
                    dependencies = " ".join([f"sleep{level-1}_{j}" for j in range(1, 1 + len(sleep_matrix_2[level-2]))])
                    f.write(f"sleep{level}_{i}:\t{dependencies}\n")
                    f.write(f"\tsleep {sleep_matrix_2[level-1][i-1]}\n")
        return sleep_matrix_2

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python generate_sleep_alea_makefile2.py <num_levels> <max_targets_per_level>")
        sys.exit(1)

    num_levels = int(sys.argv[1])
    max_targets_per_level = int(sys.argv[2])
    sleep_matrix_2 = generate_sleep_alea_makefile2(num_levels, max_targets_per_level)
    print("All To All files 2 dependencies Makefile generated successfully!")
    print("The matrix of times for this test :\n", sleep_matrix_2)
