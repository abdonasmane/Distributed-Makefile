#!/bin/bash

# Liste des tailles de messages à tester (jusqu'à 15 Go en octets)
SIZES=(
    10000000      # 10 Mo
    13000000
    16000000
    20000000
    23000000
    25000000      # 25 Mo
    50000000      # 50 Mo
    100000000     # 100 Mo
    250000000     # 250 Mo
    500000000     # 500 Mo
    1000000000    # 1 Go
    1500000000    # 1.5 Go
    2000000000    # 2 Go
    3000000000    # 3 Go
    4000000000    # 4 Go
    5000000000    # 5 Go
    6000000000    # 6 Go
    7000000000    # 7 Go
    8000000000    # 8 Go
    9000000000    # 9 Go
    10000000000   # 10 Go
    12000000000   # 12 Go
    15000000000   # 15 Go
)
# Nom du fichier pour sauvegarder les résultats
RESULTS_FILE="network_performance_results.txt"

# Vérification des arguments
if [ $# -ne 2 ]; then
    echo "Usage: $0 <server_hostname> <port>"
    exit 1
fi

SERVER_HOSTNAME=$1
PORT=$2

# Fonction pour nettoyer les processus en cours
cleanup() {
    echo "Interrupt signal received. Cleaning up..."
    kill 0  # Arrête tous les sous-processus du script
    exit 1
}

# Attrape SIGINT (Ctrl+C) pour effectuer le nettoyage
trap cleanup SIGINT

# Initialisation du fichier de résultats (écriture de l'entête si nécessaire)
if [ ! -f "$RESULTS_FILE" ]; then
    echo "Message_Size RTT(N) 1/To" > "$RESULTS_FILE"
fi

# Boucle pour tester chaque taille
for size in "${SIZES[@]}"; do
    echo "Running test with message size: $size bytes"
    
    # Exécution du script et capture de la sortie
    output=$(./network_perf_N_output.sh "$SERVER_HOSTNAME" "$PORT" "$size")
    
    # Sauvegarde des résultats
    avg_rtt=$(echo "$output" | grep "RTT(N)" | tail -n 1 | awk '{print $3}')
    avg_to=$(echo "$output" | grep "1/To" | tail -n 1 | awk '{print $3}')
    echo "$size $avg_rtt $avg_to" >> "$RESULTS_FILE"
done

echo "Results saved to $RESULTS_FILE"
