#!/usr/bin/env python3
import sys

def generate_heavy_tasks(num_targets):
    with open("Makefile", "w") as f:
        f.write("all:\t")
        for i in range(1, num_targets):
            f.write(f"sleep{i} ")
        f.write(f"sleep{num_targets}\n\n")

        for i in range(1, num_targets+1):
            f.write(f"sleep{i}:\n")
            f.write(f"\tsleep 5\n")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python generateHeavyTasks.py <num_targets>")
        sys.exit(1)

    num_targets = int(sys.argv[1])

    generate_heavy_tasks(num_targets)
    print("Heavy tasks Makefile generated successfully!")
