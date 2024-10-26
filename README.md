# Distributed Make Project

## Description
This project is a simple Java-based parser for Makefiles that generates a task dependency graph and prints the execution order of tasks. It includes topological sorting to handle dependencies and avoid cycles, making it useful for sequential and distributed task execution setups.

## Prerequisites
- Java 8 or later
- Maven

## Installation
1. Clone this repository:
   ```bash
   git clone <repository-url>
   cd <repository-directory>
   ```

2. Compile the code using Maven:
    ```bash
    mvn compile
    ```

## Usage
After compiling, you can run the MakefileParser with a specified Makefile to view its task dependencies. Replace path/to/Makefile with the path to the Makefile you want to parse:
```bash
mvn exec:java -Dexec.mainClass="MakefileParser" -Dexec.args="path/to/Makefile"
```


### This command will:
- Parse the Makefile.
- Display the task dependency graph.
- Print the execution order of tasks according to dependencies.

## Example

To parse a sample Makefile located at src/test/resources/test0/matrix/Makefile, run:
```bash
mvn exec:java -Dexec.mainClass="MakefileParser" -Dexec.args="src/test/resources/test0/matrix/Makefile"
```
The output will display the dependency graph and the order for task execution.

## Troubleshooting

Ensure Maven and Java are installed and available in your PATH. If there are issues, verify the Makefile path is correct relative to the project root.