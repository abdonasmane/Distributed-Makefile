#!/bin/bash

# Liste des tailles de messages à tester (jusqu'à 15 Go en octets)
SIZES=(
    13000000      # 13 MO
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

# Noms des fichiers de résultats
RESULTS_FILE_N="network_performance_results_N.txt"
RESULTS_FILE_IO="network_performance_results_IO.txt"

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

# Initialisation des fichiers de résultats
if [ ! -f "$RESULTS_FILE_N" ]; then
    echo "Message_Size RTT(N) 1/To" > "$RESULTS_FILE_N"
fi
if [ ! -f "$RESULTS_FILE_IO" ]; then
    echo "File_Size RTT(N) 1/To" > "$RESULTS_FILE_IO"
fi

# Création du répertoire pour les fichiers générés
OUTPUT_DIR="./generated_files"
mkdir -p "$OUTPUT_DIR"

# Génération d'un petit fichier de test (128 octets)
small_file="${OUTPUT_DIR}/file_128.bin"
dd if=/dev/urandom of="$small_file" bs=1 count=128 status=none

# Boucle pour tester chaque taille
for size in "${SIZES[@]}"; do
    echo "Running test with size: $size bytes"

    # Génération du fichier pour la taille actuelle
    large_file="${OUTPUT_DIR}/file_${size}.bin"
    dd if=/dev/urandom of="$large_file" bs=1 count="$size" status=none

    # Test de performance N (network only)
    output_n=$(./network_perf_N_output.sh "$SERVER_HOSTNAME" "$PORT" "$size")


    # Test de performance IO (avec petits et grands fichiers)
    output_io=$(./network_perf_IO_output.sh "$SERVER_HOSTNAME" "$PORT" "$small_file" "$large_file")

    # Nettoyage des fichiers générés pour éviter d'occuper trop d'espace
    rm -f "$large_file"
done

echo "Results saved to $RESULTS_FILE_N and $RESULTS_FILE_IO"
