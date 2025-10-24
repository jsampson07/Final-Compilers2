
#!/bin/bash

# Write a script to build your backend in this file 
# (As required by your chosen backend language)

#!/bin/bash

# Script to build the Tiger-IR to MIPS compiler backend

# --- Configuration ---
# Directory to store compiled .class files
BUILD_DIR="build"
# Source directory (for Demo, IR packages, etc.)
MAIN_SRC_DIR="src"
# Source directory for MIPS interpreter files
MIPS_SRC_DIR="mips-interpreter/src" # Path relative to Project-2

# --- Clean and Create Build Directory ---
echo "Cleaning previous build..."
rm -rf "$BUILD_DIR"
echo "Creating build directory: $BUILD_DIR"
mkdir -p "$BUILD_DIR"

# --- Compile Java Source Files ---
echo "Compiling Java source files..."

# Compile using multiple source paths
# -cp needs to include the roots of BOTH source trees if they reference each other
# Or use -sourcepath pointing to both roots
# Let's use -sourcepath for simplicity if packages are consistent
javac -d "$BUILD_DIR" -sourcepath "$MAIN_SRC_DIR":"$MIPS_SRC_DIR" \
    "$MAIN_SRC_DIR"/Demo.java \
    "$MAIN_SRC_DIR"/InstructionSelector.java \
    "$MAIN_SRC_DIR"/ir/*.java \
    "$MAIN_SRC_DIR"/ir/datatype/*.java \
    "$MAIN_SRC_DIR"/ir/operand/*.java \
    "$MIPS_SRC_DIR"/main/java/mips/MIPSInstruction.java \
    "$MIPS_SRC_DIR"/main/java/mips/MIPSInterpreter.java \
    "$MIPS_SRC_DIR"/main/java/mips/MIPSProgram.java \
    "$MIPS_SRC_DIR"/main/java/mips/MIPSOp.java \
    "$MIPS_SRC_DIR"/main/java/mips/MemLayout.java \
    "$MIPS_SRC_DIR"/main/java/mips/MIPSPrinter.java \
    "$MIPS_SRC_DIR"/main/java/mips/operand/*.java
    # Add any other necessary .java files from either source directory,
    # ensuring the path starts with $MAIN_SRC_DIR or $MIPS_SRC_DIR
    # e.g.:
    # "$MAIN_SRC_DIR"/IRcfg.java \
    # "$MAIN_SRC_DIR"/IRNode.java \
    # ... etc

# Check if compilation was successful
if [ $? -ne 0 ]; then
    echo "Error: Compilation failed."
    exit 1
else
    echo "Build completed successfully."
fi

exit 0