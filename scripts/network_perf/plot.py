import matplotlib.pyplot as plt

# Charger les résultats
sizes = []
rtt = []
throughput = []

with open("network_performance_results.txt", "r") as f:
    next(f)  # Sauter la ligne d'en-tête
    for line in f:
        size, avg_rtt, avg_to = map(float, line.split())
        sizes.append(size)
        rtt.append(avg_rtt)
        throughput.append(avg_to)

# Dessiner la courbe RTT(N)
plt.figure(figsize=(10, 6))
plt.plot(sizes, rtt, marker='o', label='RTT(N)')
plt.xscale('log')
plt.xlabel('Message Size (bytes)')
plt.ylabel('RTT(N) (seconds)')
plt.title('RTT(N) en fonction de Message Size')
plt.legend()
plt.grid()
plt.show()

# Dessiner la courbe de débit
plt.figure(figsize=(10, 6))
plt.plot(sizes, throughput, marker='o', label='Debit (1/To)')
plt.xscale('log')
plt.yscale('log')
plt.xlabel('Message Size (bytes)')
plt.ylabel('Debit (bytes/second)')
plt.title('Debit en fonction de Message Size')
plt.legend()
plt.grid()
plt.show()
