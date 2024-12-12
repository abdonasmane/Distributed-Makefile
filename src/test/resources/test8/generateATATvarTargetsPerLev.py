#!/usr/bin/env python3
import random
import sys
from collections import defaultdict
random.seed(42)

def generate_all_to_all_makefile2(interval_length, num_levels, max_targets_per_level):
    with open('Makefile', 'w') as f:
        old_files = defaultdict(int)
        for level in range(1, num_levels + 1):
            old_files[level] = random.randint(2, max_targets_per_level)
        all_targets = [f"list{level}_{i}" for level in range(1, num_levels + 1) for i in range(1, old_files[level] + 1)]
        f.write("all: " + " ".join(all_targets) + "\n")

        # Rule for compiling the premier program
        f.write("premiera:\tpremier.c\n")
        f.write("\tgcc premier.c -o premier -lm\n")
        
        # Generate rules for each level
        for level in range(1, num_levels + 1):
            for i in range(1, old_files[level] + 1):
                start = random.randint(2, interval_length - 100)
                end = random.randint(start+1, interval_length)
                if level == 1:
                    # Level 1 files: No dependencies, simple range calculation
                    f.write(f"list{level}_{i}:\tpremiera\n")
                    f.write(f"\t./premier {start} {end} > list{level}_{i}.txt\n")
                else:
                    # Higher-level files depend on all previous levels
                    dependencies = " ".join([f"list{level-1}_{j}" for j in random.sample(range(1, old_files[level-1] + 1), min(old_files[level-1] - 1, 20))])
                    f.write(f"list{level}_{i}:\t{dependencies}\n")
                    f.write(f"\t./premier {start} {end} >> list{level}_{i}.txt\n")

        # Clean target
        f.write("clean:\n")
        f.write("\trm -rf list* premier\n")

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python generateATATvarTargetsPerLev.py <interval_length> <num_levels> <max_targets_per_level>")
        sys.exit(1)

    interval_length = int(sys.argv[1])
    num_levels = int(sys.argv[2])
    max_targets_per_level = int(sys.argv[3])
    generate_all_to_all_makefile2(interval_length, num_levels, max_targets_per_level)
    print("All To All files 2 dependencies Makefile generated successfully!")
