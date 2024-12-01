alias spark-shell="/home/anasmane/spark-3.5.3-bin-hadoop3/bin/spark-shell"
export MAVEN_OPTS="-Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8"
SPARK_VERSION="3.5.3"
export SPARK_HOME=~/spark-${SPARK_VERSION}-bin-hadoop3
export PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin

#THIS MUST BE AT THE END OF THE FILE FOR SDKMAN TO WORK!!!
export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"