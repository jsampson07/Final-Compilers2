#!/bin/bash

# Write a script to run your backend
# This script must take exactly one terminal argument:
# a path to an input IR file. It should also take one
# flag, either ``--naive`` or ``--greedy`` to indicate
# either the naive or intra-block greedy allocation algorithm.
# This script should output a file ``out.s``.

# For example, if a Tiger-IR file is located at path/to/file.ir,
# then it should be possible to run run.sh as follows.

# run.sh path/to/file.ir --naive
# Produces out . s
# run.sh path/to/file.ir --greedy
# Produces out . s

# where .class files are from build.sh
BUILD_DIR="build"

INPUT_IR="$1"
ALLOC_TYPE="$2"

# let's see if the build directory exists yet, if not ==> error !!!
if [ ! -d "$BUILD_DIR" ]; then
    echo "Build directory not found. Run ./build.sh first."
    exit 1
fi
# now let's check that the input file does exist
if [ ! -f "$INPUT_IR" ]; then
    echo "Input IR file not found. Please try again but with a valid IR file."
    exit 1
fi

# now let's make sure that the allocation type is valid
if [ "$ALLOC_TYPE" != "--naive" ] && [ "$ALLOC_TYPE" != "--greedy" ]; then
    echo "Invalid allocation type flag. Use either --naive or --greedy."
    exit 1
fi
# we are done sanitizing input

java -cp "$BUILD_DIR" "Demo" "$INPUT_IR" "out.s"

# Check if Java execution was successful
if [ $? -ne 0 ]; then
    echo "Compiler backend execution FAILED!!!"
    exit 1
else
    echo "Compiler SUCCESSFULLY executed!!!"
fi

exit 0