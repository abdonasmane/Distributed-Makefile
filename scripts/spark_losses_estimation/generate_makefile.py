#!/usr/bin/env python3
import random
import sys
random.seed(42)

def generate_makefile(num_levels, targets_per_level):
    with open('Makefile', 'w') as f:
        all_targets = [f"list{num_levels}_{i}" for i in range(1, targets_per_level + 1)]
        f.write("all: " + " ".join(all_targets) + "\n")
        
        for level in range(1, num_levels + 1):
            for i in range(1, targets_per_level + 1):
                if level == 1:
                    # Level 1 files: No dependencies, simple range calculation
                    f.write(f"list{level}_{i}:\n")
                else:
                    dependencies = " ".join([f"list{level-1}_{j}" for j in range(1, targets_per_level + 1)])
                    f.write(f"list{level}_{i}:\t {dependencies}\n")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python generate_makefile.py <num_levels> <targets_per_level>")
        sys.exit(1)

    num_levels = int(sys.argv[1])
    targets_per_level = int(sys.argv[2])
    generate_makefile(num_levels, targets_per_level)
