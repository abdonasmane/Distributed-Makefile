#!/bin/bash

# Update the package lists
sudo-g5k apt-get update

# Download and extract Apache Spark
SPARK_VERSION="3.5.3"  # Replace with the version you need
wget https://dlcdn.apache.org/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop3.tgz

# Extract Spark
tar -xzf spark-${SPARK_VERSION}-bin-hadoop3.tgz
rm spark-${SPARK_VERSION}-bin-hadoop3.tgz

# install compatible java version
curl -s "https://get.sdkman.io" | bash
source ~/.bashrc
sdk install java 8.0.332-zulu
sdk default java 8.0.332-zulu

echo "Installation complete!"
