#!/usr/bin/env python3
import sys
import random
random.seed(42)


def generate_unbalanced_tree_makefile(interval_length, num_lists):
    with open("Makefile", "w") as f:
        # Write all targets
        f.write("all:\t")
        for i in range(1, num_lists):
            f.write(f"list{i} ")
        f.write(f"list{num_lists}\n")

        # Write cp command to merge lists
        f.write("\tcp list1.txt list.txt\n")
        # for i in range(2, num_lists + 1):
        #     f.write(f"\tcat list{i}.txt >> list.txt\n")

        # Write compilation rule
        f.write("premiera:\tpremier.c\n")
        f.write("\tgcc premier.c -o premier -lm\n")

        # Write unbalanced intervals for each list
        i = 0
        while i <= num_lists:
            start = random.randint(2, interval_length-100)
            end = random.randint(start + 1, interval_length)
            f.write(f"list{i}:\tpremiera\n")
            f.write(f"\t./premier {start} {end} > list{i}.txt\n")
            i += 1

        # Write clean rule
        f.write("clean:\n")
        f.write("\trm -rf list* premier\n")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python generateUnbalancedTreeMakefile.py <interval_length> <num_lists>")
        sys.exit(1)

    interval_length = int(sys.argv[1])
    num_lists = int(sys.argv[2])

    generate_unbalanced_tree_makefile(interval_length, num_lists)
    print("Unbalanced Makefile generated successfully!")
