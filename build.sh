
#!/bin/bash

# Write a script to build your backend in this file 
# (As required by your chosen backend language)

#store .class files here!!!
BUILD_DIR="build"
# where src files are for Tiger-IR part (optimization)
IR_SRC_DIR="src"
# where src files are for MIPS part of project (instruction select + register allocation)
MIPS_SRC_DIR="mips-interpreter/src"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

javac -d "$BUILD_DIR" -sourcepath "$IR_SRC_DIR":"$MIPS_SRC_DIR" "$IR_SRC_DIR"/Demo.java "$IR_SRC_DIR"/InstructionSelector.java \
    "$IR_SRC_DIR"/ir/*.java "$IR_SRC_DIR"/ir/datatype/*.java "$IR_SRC_DIR"/ir/operand/*.java "$MIPS_SRC_DIR"/main/java/mips/MIPSInstruction.java \
    "$MIPS_SRC_DIR"/main/java/mips/MIPSInterpreter.java "$MIPS_SRC_DIR"/main/java/mips/MIPSProgram.java "$MIPS_SRC_DIR"/main/java/mips/MIPSOp.java \
    "$MIPS_SRC_DIR"/main/java/mips/MemLayout.java "$MIPS_SRC_DIR"/main/java/mips/operand/*.java
if [ $? -ne 0 ]; then
    echo "Compilation FAILED!!!"
    exit 1
else
    echo "Build completed SUCCESSFULLY!!!"
fi

exit 0