import matplotlib.pyplot as plt

# Charger les résultats pour Network (N)
sizes_n, rtt_n, throughput_n = [], [], []
with open("network_performance_results_N.txt", "r") as f:
    next(f)  # Sauter la ligne d'en-tête
    for line in f:
        size, avg_rtt, avg_to = map(float, line.split())
        sizes_n.append(size)
        rtt_n.append(avg_rtt)
        throughput_n.append(avg_to)

# Charger les résultats pour IO
sizes_io, rtt_io, throughput_io = [], [], []
with open("network_performance_results_IO.txt", "r") as f:
    next(f)  # Sauter la ligne d'en-tête
    for line in f:
        size, avg_rtt, avg_to = map(float, line.split())
        sizes_io.append(size)
        rtt_io.append(avg_rtt)
        throughput_io.append(avg_to)

# Créer les sous-graphiques
fig, axs = plt.subplots(2, 2, figsize=(15, 10))

# Graphique RTT(N) (Network)
axs[0, 0].plot(sizes_n, rtt_n, marker='o', label='RTT(N)', color='b')
axs[0, 0].set_xscale('log')
axs[0, 0].set_xlabel('Message Size (bytes)')
axs[0, 0].set_ylabel('RTT(N) (seconds)')
axs[0, 0].set_title('RTT(N) en fonction de Message Size (Network)')
axs[0, 0].grid(which='both', linestyle='--', linewidth=0.5)
axs[0, 0].legend()

# Graphique débit (Network)
axs[1, 0].plot(sizes_n, throughput_n, marker='o', label='Throughput (1/To)', color='g')
axs[1, 0].set_xscale('log')
axs[1, 0].set_yscale('log')
axs[1, 0].set_ylim(3e8, 1.5e9)  # Étendre la plage de 300 Mo/s à 1,5 Go/s
axs[1, 0].set_xlabel('Message Size (bytes)')
axs[1, 0].set_ylabel('Throughput (bytes/second)')
axs[1, 0].set_title('Debit en fonction de Message Size (Network)')
axs[1, 0].grid(which='both', linestyle='--', linewidth=0.5)
axs[1, 0].legend()

# Graphique RTT(IO)
axs[0, 1].plot(sizes_io, rtt_io, marker='o', label='RTT(IO)', color='r')
axs[0, 1].set_xscale('log')
axs[0, 1].set_xlabel('File Size (bytes)')
axs[0, 1].set_ylabel('RTT(IO) (seconds)')
axs[0, 1].set_title('RTT(IO) en fonction de File Size')
axs[0, 1].grid(which='both', linestyle='--', linewidth=0.5)
axs[0, 1].legend()

# Graphique débit (IO)
axs[1, 1].plot(sizes_io, throughput_io, marker='o', label='Throughput (1/To)', color='m')
axs[1, 1].set_xscale('log')
axs[1, 1].set_yscale('log')
axs[1, 1].set_ylim(3e8, 1.5e9)  # Étendre la plage de 300 Mo/s à 1,5 Go/s
axs[1, 1].set_xlabel('File Size (bytes)')
axs[1, 1].set_ylabel('Throughput (bytes/second)')
axs[1, 1].set_title('Debit en fonction de File Size')
axs[1, 1].grid(which='both', linestyle='--', linewidth=0.5)
axs[1, 1].legend()

# Ajuster l'espacement entre les graphiques
plt.tight_layout()

# Afficher les graphiques
plt.show()
