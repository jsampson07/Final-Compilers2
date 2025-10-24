
#!/bin/bash

# Write a script to build your backend in this file 
# (As required by your chosen backend language)

#!/bin/bash

#store .class files here!!!
BUILD_DIR="build"
# where src files are for Tiger-IR part (optimization)
IR_SRC_DIR="src"
# where src files are for MIPS part of project (instruction select + register allocation)
MIPS_SRC_DIR="mips-interpreter/src"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Compile using multiple source paths
# -cp needs to include the roots of BOTH source trees if they reference each other
# Or use -sourcepath pointing to both roots
# Let's use -sourcepath for simplicity if packages are consistent
javac -d "$BUILD_DIR" -sourcepath "$IR_SRC_DIR":"$MIPS_SRC_DIR" \
    "$IR_SRC_DIR"/Demo.java \
    "$IR_SRC_DIR"/InstructionSelector.java \
    "$IR_SRC_DIR"/ir/*.java \
    "$IR_SRC_DIR"/ir/datatype/*.java \
    "$IR_SRC_DIR"/ir/operand/*.java \
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