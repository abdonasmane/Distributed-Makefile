import sys
import matplotlib.pyplot as plt
import os

def parse_file(file_path):
    """
    Parse the file and return the data as lists of tuples (size, RTT, 1/To).
    """
    with open(file_path, 'r') as f:
        lines = f.readlines()
    
    data = []
    for line in lines[1:]:  # Skip the header
        parts = line.split()
        if len(parts) != 3:
            continue  # Skip invalid lines
        size, rtt, inv_to = map(float, parts)
        data.append((size, rtt, inv_to))
    return data

def extract_name(file_path):
    """
    Extract the <name> from the file path, considering underscores and suffixes.
    """
    base_name = os.path.basename(file_path)
    return "_".join(base_name.split('_')[:-1])

def plot_graphs(name, data_n, data_io, output_file):
    """
    Plot the required graphs and save them to a PNG file.
    """
    # Extract values for plotting
    msg_size_n, rtt_n, inv_to_n = zip(*data_n)
    file_size_io, rtt_io, inv_to_io = zip(*data_io)

    fig, axes = plt.subplots(2, 2, figsize=(12, 8))

    # Plot RTT vs Message_Size
    axes[0, 0].plot(msg_size_n, rtt_n, marker='o', label='RTT vs Message_Size')
    axes[0, 0].set_xlabel('Message Size')
    axes[0, 0].set_ylabel('RTT')
    axes[0, 0].set_title('RTT vs Message_Size')
    axes[0, 0].grid(True)
    axes[0, 0].legend()

    # Plot 1/To vs Message_Size
    axes[0, 1].plot(msg_size_n, inv_to_n, marker='o', label='1/To vs Message_Size', color='orange')
    axes[0, 1].set_xlabel('Message Size')
    axes[0, 1].set_ylabel('1/To')
    axes[0, 1].set_title('1/To vs Message_Size')
    axes[0, 1].grid(True)
    axes[0, 1].legend()

    # Plot RTT vs File_Size
    axes[1, 0].plot(file_size_io, rtt_io, marker='o', label='RTT vs File_Size', color='green')
    axes[1, 0].set_xlabel('File Size')
    axes[1, 0].set_ylabel('RTT')
    axes[1, 0].set_title('RTT vs File_Size')
    axes[1, 0].grid(True)
    axes[1, 0].legend()

    # Plot 1/To vs File_Size
    axes[1, 1].plot(file_size_io, inv_to_io, marker='o', label='1/To vs File_Size', color='red')
    axes[1, 1].set_xlabel('File Size')
    axes[1, 1].set_ylabel('1/To')
    axes[1, 1].set_title('1/To vs File_Size')
    axes[1, 1].grid(True)
    axes[1, 1].legend()

    # Adjust layout and save the figure
    plt.tight_layout()
    plt.savefig(output_file)
    print(f"Saved figure as {output_file}")

def main():
    if len(sys.argv) != 3:
        print("Usage: python script.py <file_N_path> <file_IO_path>")
        sys.exit(1)

    file_n_path = sys.argv[1]
    file_io_path = sys.argv[2]

    # Extract the common <name> from the file paths
    name_n = extract_name(file_n_path)
    name_io = extract_name(file_io_path)

    if name_n != name_io:
        print("Error: File names do not have the same <name> prefix.")
        sys.exit(1)
    
    name = name_n  # Use the shared name
    output_file = f"{name}.png"

    # Parse the input files
    data_n = parse_file(file_n_path)
    data_io = parse_file(file_io_path)

    # Plot and save the graphs
    plot_graphs(name, data_n, data_io, output_file)

if __name__ == "__main__":
    main()
