import main.java.mips.MIPSInstruction;
import main.java.mips.MIPSProgram;
import main.java.mips.MIPSOp;
import java.io.PrintStream;

public class MIPSPrinter {

    private PrintStream output;

    public MIPSPrinter(PrintStream outputStream) {
        this.output = outputStream;
    }

    public void printProgram(MIPSProgram program) {

        // Print Text Segment
        output.println(".text");


        // Print Instructions
        if (program.instruction_list != null) {
            for (MIPSInstruction instruction : program.instruction_list) {
                // Handle Labels separately for formatting
                if (instruction.op == MIPSOp.LABEL && instruction.label != null) {
                    output.println(instruction.label + ":"); // Print label with colon, no indentation
                } else {
                    // Use a helper to format non-label instructions without the label part
                    output.println("  " + formatInstructionOnly(instruction)); // Indent instructions
                }
            }
        }
    }

    private String formatInstructionOnly(MIPSInstruction instruction) {
        StringBuilder builder = new StringBuilder();
        builder.append(instruction.op.toString()); // Opcode first
        if (!instruction.operands.isEmpty()) {
            builder.append(' '); // Space before operands
            for (int i = 0; i < instruction.operands.size() - 1; i++) {
                builder.append(instruction.operands.get(i).toString());
                builder.append(", ");
            }
            builder.append(instruction.operands.get(instruction.operands.size() - 1).toString()); // Last operand
        }
        return builder.toString();
    }
}