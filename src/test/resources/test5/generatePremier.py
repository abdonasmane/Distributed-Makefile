#!/usr/bin/env python3
import sys

def generate_makefile(interval_length, num_lists):
    with open("Makefile", "w") as f:
        f.write("all:\t")
        for i in range(1, num_lists):
            f.write(f"list{i} ")
        f.write(f"list{num_lists}\n")
        f.write("\tcp list1.txt list.txt\n")
        # for i in range(1, num_lists):
        #     f.write(f"cat list{i}.txt >> list.txt\n")
        # f.write(f"cat list{num_lists}.txt >> list.txt\n")

        f.write("premiera:\tpremier.c\n")
        f.write("\tgcc premier.c -o premier -lm\n")
        
        f.write("list1:\tpremiera\n")
        f.write(f"\t./premier 2 `echo 1*{interval_length}/{num_lists}-1 |bc` > list1.txt\n")

        for i in range(2, num_lists+1):
            f.write(f"list{i}:\tpremiera\n")
            f.write(f"\t./premier `echo {i-1}*{interval_length}/{num_lists} |bc` `echo {i}*{interval_length}/{num_lists}-1 |bc` > list{i}.txt\n")
        f.write("clean:\n")
        f.write("\trm -rf list* premier\n")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python generate_makefile.py <interval_length> <num_lists>")
        sys.exit(1)

    interval_length = int(sys.argv[1])
    num_lists = int(sys.argv[2])

    generate_makefile(interval_length, num_lists)
    print("Makefile generated successfully!")
