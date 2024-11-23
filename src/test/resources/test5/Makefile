# Simple Makefile

all: target1 target2 final_target

# Dependencies for target1
target1: dep1 dep2
	echo "Building target1 from dep1 and dep2" > target1

# Dependencies for target2
target2: dep3
	echo "Building target2 from dep3" > target2

# Final target depends on target1 and target2
final_target: target1 target2
	echo "Combining target1 and target2 into final_target" > final_target

# Creating simple dependencies
dep1:
	echo "This is dep1" > dep1

dep2:
	echo "This is dep2" > dep2

dep3:
	echo "This is dep3" > dep3

shouldnotRun:
	echo "hhhhhhhhhhhhh you lost"

# Clean up generated files
clean:
	rm -f target1 target2 final_target dep1 dep2 dep3
