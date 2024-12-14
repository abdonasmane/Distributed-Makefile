#!/usr/bin/env python3
import random
import sys
random.seed(42)

def generate_all_to_all_makefile(num_levels, targets_per_level):
    with open('Makefile', 'w') as f:
        all_targets = [f"list{level}_{i}" for level in range(1, num_levels + 1) for i in range(1, targets_per_level + 1)]
        f.write("all: " + " ".join(all_targets) + "\n")

        # Rule for compiling the premier program
        f.write("premiera: premier.c\n")
        f.write("\tgcc premier.c -o premier -lm\n")
        
        # Generate rules for each level
        start = 1000
        end = 2000
        for level in range(1, num_levels + 1):
            for i in range(1, targets_per_level + 1):
                if level == 1:
                    # Level 1 files: No dependencies, simple range calculation
                    f.write(f"list{level}_{i}:\tpremiera\n")
                    f.write(f"\t./premier {start} {end} > list{level}_{i}.txt\n")
                else:
                    # Higher-level files depend on all previous levels
                    dependencies = " ".join([f"list{level-1}_{j}" for j in range(1, targets_per_level + 1)])
                    f.write(f"list{level}_{i}:\tpremiera {dependencies}\n")
                    f.write(f"\t./premier {start} {end} >> list{level}_{i}.txt\n")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python generateAllToAllTree.py <num_levels> <targets_per_level>")
        sys.exit(1)

    num_levels = int(sys.argv[1])
    targets_per_level = int(sys.argv[2])
    generate_all_to_all_makefile(num_levels, targets_per_level)
