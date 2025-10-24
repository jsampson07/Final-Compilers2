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


#!/bin/bash

# Script to run the Tiger-IR to MIPS compiler backend
# Usage: ./run.sh <input_ir_file> <--naive | --greedy>

# --- Configuration ---
# Build directory where .class files are located
BUILD_DIR="build"
# Main Java class to execute (Update if Demo.java has a package declaration)
# e.g., if package ir;, use MAIN_CLASS="ir.Demo"
MAIN_CLASS="Demo"
# Fixed output filename as required by project spec
OUTPUT_S_FILE="out.s"

# --- Argument Parsing ---
# Check for correct number of arguments (input file + flag)
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <input_ir_file> <--naive | --greedy>"
    exit 1
fi

# Assign arguments
INPUT_IR="$1"
ALLOC_FLAG="$2"

# Validate the allocation flag
if [ "$ALLOC_FLAG" != "--naive" ] && [ "$ALLOC_FLAG" != "--greedy" ]; then
    echo "Error: Invalid allocation flag '$ALLOC_FLAG'. Use --naive or --greedy."
    exit 1
fi

# --- Check Prerequisites ---
# Check if build directory exists
if [ ! -d "$BUILD_DIR" ]; then
    echo "Error: Build directory '$BUILD_DIR' not found. Run build.sh first."
    exit 1
fi
# Check if input IR file exists
if [ ! -f "$INPUT_IR" ]; then
    echo "Error: Input IR file '$INPUT_IR' not found."
    exit 1
fi

# --- Run the Java Compiler Backend ---
echo "Running compiler backend..."
echo "Input IR: $INPUT_IR"
echo "Output MIPS: $OUTPUT_S_FILE"
echo "Allocation: $ALLOC_FLAG"

# Execute the main Java class, setting the classpath to the build directory
# Pass the input IR path and the fixed output MIPS path ("out.s") as args to main()
java -cp "$BUILD_DIR" "$MAIN_CLASS" "$INPUT_IR" "$OUTPUT_S_FILE"

# Check if Java execution was successful
if [ $? -ne 0 ]; then
    echo "Error: Compiler backend execution failed."
    exit 1
else
    echo "Compiler backend finished. MIPS code saved to $OUTPUT_S_FILE"
fi

# --- Script Success ---
exit 0