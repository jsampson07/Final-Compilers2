import main.java.mips.MIPSInstruction;
import main.java.mips.MIPSProgram;
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
                // MIPSInstruction.toString() should handle formatting label:, op, operands
                output.println("  " + instruction.toString()); // Indent instructions for readability
            }
        }
    }
}