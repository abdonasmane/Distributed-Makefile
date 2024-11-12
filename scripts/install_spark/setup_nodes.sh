#!/bin/bash

# Update the package lists
sudo-g5k apt-get update

# Download and extract Apache Spark
SPARK_VERSION="3.5.3"  # Replace with the version you need
wget https://dlcdn.apache.org/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop3.tgz

# Extract Spark
tar -xzf spark-${SPARK_VERSION}-bin-hadoop3.tgz
rm spark-${SPARK_VERSION}-bin-hadoop3.tgz

# Set environment variables for Spark
export SPARK_HOME=~/spark-${SPARK_VERSION}-bin-hadoop3
export PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin
echo "alias spark-shell=\"$SPARK_HOME/bin/spark-shell\"" >> ~/.bashrc

source ~/.bashrc

# Verify Spark installation
${SPARK_HOME}/bin/spark-shell --version

echo "Installation complete!"
