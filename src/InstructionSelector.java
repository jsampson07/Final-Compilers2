import ir.*;
import ir.datatype.IRArrayType;
import ir.operand.*;
import main.java.mips.*;
import main.java.mips.operand.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger; // For unique labels

public class InstructionSelector {
    // --- Fields ---
    public MIPSProgram mips_program;
    public Map<String, Integer> offsets_stack;
    private static AtomicInteger labelCounter = new AtomicInteger(0); // For generating unique labels

    public static final int WORD_SIZE = 4;

    // --- Physical Registers ---
    public Register fp = new Register("$fp", false);
    public Register ra = new Register("$ra", false);
    public Register sp = new Register("$sp", false);
    public Register v0 = new Register("$v0", false);
    public Register v1 = new Register("$v1", false);
    public Register a0 = new Register("$a0", false);
    public Register a1 = new Register("$a1", false);
    public Register a2 = new Register("$a2", false);
    public Register a3 = new Register("$a3", false);
    public Register t0 = new Register("$t0", false);
    public Register t1 = new Register("$t1", false);
    public Register t2 = new Register("$t2", false);
    public Register t3 = new Register("$t3", false);
    // Add t4-t9 if needed
    public Register zero = new Register("$zero", false);

    // --- State ---
    public int current_frame_size;
    public boolean current_is_non_leaf;

    // Helper to generate unique label names
    private String get_unique_label(String prefix) {
        return prefix + "_" + labelCounter.getAndIncrement();
    }

    // --- Main Method ---
    public MIPSProgram select_instructions(IRProgram ir_program) {
        if (ir_program == null) {
            System.out.println("there is a NULLLLL program!!!!!");
            return null;
        }
        System.out.println("HELLLO ARE WE INSIDE OF SELECT_INSTRUCTIONS????");
        mips_program = new MIPSProgram();
        System.out.println("ARE WE PASSED THIS??");

        for (IRFunction function : ir_program.functions) {
            System.out.println("\nwe are inside the LOOP\n");
            translate_function(function);
        }
        return mips_program;
    }

    public void translate_function(IRFunction function) {
        // [Prologue code - largely unchanged from previous version]
        // ... (Includes frame size calculation, saving $ra/$fp, setting $fp, mapping offsets) ...
        // 1. Initialize function-specific state
        System.out.println("HELLO I HAVE ENTERED THE FUNCTION WOOWOHOOO.");
        offsets_stack = new HashMap<>();
        current_is_non_leaf = false;
        int max_num_args_called = 0;

        // --- Determine Space Requirements ---

        // 2a. Check if non-leaf and find max outgoing args needed
        for (IRInstruction instruc : function.instructions) {
             if (instruc.opCode == IRInstruction.OpCode.CALL || instruc.opCode == IRInstruction.OpCode.CALLR) {
                current_is_non_leaf = true;
                String func_name = "";
                int num_ir_operands = instruc.operands.length;
                int first_arg_index = -1;

                if (instruc.opCode == IRInstruction.OpCode.CALL) {
                    func_name = ((IRFunctionOperand) instruc.operands[0]).getName();
                    first_arg_index = 1;
                } else { // CALLR
                    IROperand funcTargetOp = instruc.operands[1];
                    if (funcTargetOp instanceof IRFunctionOperand) {
                        func_name = ((IRFunctionOperand) funcTargetOp).getName();
                    } // Else it's a variable, check name later if needed for intrinsics
                    first_arg_index = 2;
                }

                if (!is_intrinsic(func_name)) { // Check only for non-intrinsics
                     int num_args = (first_arg_index != -1) ? (num_ir_operands - first_arg_index) : 0;
                     max_num_args_called = Math.max(max_num_args_called, num_args);
                }
            }
        }

        // 2b. Space for Outgoing Arguments (bottom of frame)
        int outgoing_arg_bytes = Math.max(4, max_num_args_called) * WORD_SIZE;

        // 2c. Space for Local Variables & Arrays (on stack)
        int local_var_bytes = 0;
        for (IRVariableOperand var : function.variables) {
            local_var_bytes += (var.type instanceof IRArrayType) ?
                               ((IRArrayType) var.type).getSize() * WORD_SIZE :
                               WORD_SIZE;
        }

        // 2d. Space for saving incoming $a0-$a3 (on stack)
        int saved_args_bytes = 0;
         for(int i = 0; i < function.parameters.size() && i < 4; i++) {
            saved_args_bytes += WORD_SIZE;
        }

        // 2e. Space for saving $ra and $fp (on stack)
        int saved_ra_fp_bytes = 0;
        boolean needs_frame = saved_args_bytes > 0 || local_var_bytes > 0 || outgoing_arg_bytes > 0 || current_is_non_leaf;
        if (needs_frame) {
            saved_ra_fp_bytes += WORD_SIZE; // Need space for $fp
             if (current_is_non_leaf) {
                saved_ra_fp_bytes += WORD_SIZE; // Also need space for $ra
            }
        }

        // --- Calculate Total Size and Align ---
        int preliminary_size = outgoing_arg_bytes + local_var_bytes + saved_args_bytes + saved_ra_fp_bytes;
        current_frame_size = preliminary_size;
        if (current_frame_size > 0 && current_frame_size % 8 != 0) {
            current_frame_size += (8 - (current_frame_size % 8));
        }

        // --- Generate Function Prologue ---
        add_regular_to_mips(MIPSOp.LABEL, new Addr(function.name));

        if (current_frame_size > 0) {
            // 1. Allocate frame
            add_regular_to_mips(MIPSOp.ADDI, sp, sp, new Imm("-" + current_frame_size, "DEC")); // Use ADDI

            int current_save_offset = current_frame_size; // Relative to new $sp

            // 2. Save $ra (if non-leaf)
            if (current_is_non_leaf) {
                current_save_offset -= WORD_SIZE;
                add_regular_to_mips(MIPSOp.SW, ra, new Addr(new Imm("" + current_save_offset, "DEC"), sp));
                offsets_stack.put("$ra_save_slot", current_save_offset);
            }

            // 3. Save old $fp
            current_save_offset -= WORD_SIZE;
            add_regular_to_mips(MIPSOp.SW, fp, new Addr(new Imm("" + current_save_offset, "DEC"), sp));
            offsets_stack.put("$fp_save_slot", current_save_offset);

            // 4. Set NEW $fp
            add_regular_to_mips(MIPSOp.ADDI, fp, sp, new Imm("" + current_save_offset, "DEC")); // Use ADDI

            // --- Map Stack Slots Relative to NEW $fp ---
            int current_offset = 0; // Base for negative offsets from $fp

            // Map and Save incoming $a0-$a3
             for (int i = 0; i < function.parameters.size() && i < 4; i++) {
                 current_offset -= WORD_SIZE;
                 Register arg_reg = get_arg_register(i);
                 add_regular_to_mips(MIPSOp.SW, arg_reg, new Addr(new Imm("" + current_offset, "DEC"), fp));
                 offsets_stack.put(function.parameters.get(i).getName(), current_offset);
            }

            // Map Local Variables & Temporaries
            for (IRVariableOperand var : function.variables) {
                int size = (var.type instanceof IRArrayType) ?
                            ((IRArrayType) var.type).getSize() * WORD_SIZE :
                            WORD_SIZE;
                current_offset -= size;
                offsets_stack.put(var.getName(), current_offset);
            }

            // Map Incoming Arguments 5+
            int arg5_base_offset = (current_is_non_leaf ? 8 : 4);
            for (int i = 4; i < function.parameters.size(); i++) {
                int arg_fp_offset = arg5_base_offset + (i - 4) * WORD_SIZE;
                offsets_stack.put(function.parameters.get(i).getName(), arg_fp_offset);
            }
        } // End if (current_frame_size > 0)

        // --- Translate instructions using Naive Allocation ---
        for (IRInstruction instruc : function.instructions) {
             translate_ir_to_mips(instruc, function);
        }


        


        // DO WE EVEN NEED THIS ANYMORE??????????????????
        if (function.name.equals("main")) {
            add_regular_to_mips(MIPSOp.LI, v0, new Imm("10", "DEC")); // code 10 = exit
            add_regular_to_mips(MIPSOp.SYSCALL);
        } else {
            // Add implicit epilogue for non-main functions
            // This prevents "fall-through"
            if (current_frame_size > 0) {
                // Restore $ra
                if (current_is_non_leaf) {
                    // This logic is from your case RETURN
                    int ra_save_offset = 4; // Relative to $fp
                    add_regular_to_mips(MIPSOp.LW, ra, new Addr(new Imm("" + ra_save_offset, "DEC"), fp));
                }
                // Restore old $fp
                int fp_save_offset = 0; // Relative to $fp
                add_regular_to_mips(MIPSOp.LW, fp, new Addr(new Imm("" + fp_save_offset, "DEC"), fp));
                
                // Deallocate frame
                add_regular_to_mips(MIPSOp.ADDI, sp, sp, new Imm("" + current_frame_size, "DEC")); 
            }
            add_regular_to_mips(MIPSOp.JR, ra); // Return to caller
        }
    } // End translate_function


    public void translate_ir_to_mips(IRInstruction instruc, IRFunction func) {

        if (instruc == null || instruc.opCode == null) return;

        switch(instruc.opCode) {
            case LABEL:
                String l_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String l_unique_label = func.name + "_" + l_label_name;
                add_regular_to_mips(MIPSOp.LABEL, new Addr(l_unique_label));
                break;

            case ASSIGN: { // assign, dest, src OR assign, array, size, value
                if (instruc.operands.length == 2) {
                    // assign, destVar, src
                    IROperand destOp = instruc.operands[0];
                    IROperand srcOp = instruc.operands[1];
                    // ... (rest is same as previous version) ...
                    if (!(destOp instanceof IRVariableOperand)) {
                        System.err.println("Error: Destination of ASSIGN must be a variable/temp.");
                        break;
                    }
                    String destName = ((IRVariableOperand) destOp).getName();
                    int destOffset = offsets_stack.get(destName);
                    load_operand_to_physical_reg(srcOp, t0); // $t0 = src
                    add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + destOffset, "DEC"), fp)); // Mem[fp+destOffset] = $t0

                } else if (instruc.operands.length == 3) {
                    // assign, arrayName, size, value (Array Initialization)
                    // ... (rest is mostly same as previous version, using ADD/ADDI) ...
                     IROperand arrayOp = instruc.operands[0];
                    IROperand sizeOp = instruc.operands[1];
                    IROperand valueOp = instruc.operands[2];

                    if (!(arrayOp instanceof IRVariableOperand) || !(sizeOp instanceof IRConstantOperand)) {
                         System.err.println("Error: Invalid operands for 3-operand ASSIGN (array init).");
                         break;
                    }
                    String arrayName = ((IRVariableOperand) arrayOp).getName();
                    int arrayBaseOffset = offsets_stack.get(arrayName);
                    int size = Integer.parseInt(((IRConstantOperand) sizeOp).getValueString());

                    load_operand_to_physical_reg(valueOp, t0); // $t0 = value
                    add_regular_to_mips(MIPSOp.LI, t1, new Imm("0", "DEC")); // $t1 = 0 (i=0)

                    String loopLabel = get_unique_label("array_init_loop");
                    String endLoopLabel = get_unique_label("array_init_end");

                    add_regular_to_mips(MIPSOp.LABEL, new Addr(loopLabel));
                    // Use BGE pseudo-op for check: if $t1 >= size, goto endLoopLabel
                    // Need size in a register first ($t3)
                    add_regular_to_mips(MIPSOp.LI, t3, new Imm(""+size, "DEC"));
                    add_regular_to_mips(MIPSOp.BGE, new Addr(endLoopLabel), t1, t3);

                    // Calculate address into $t2
                    Register vrAddr = calculate_array_address(arrayBaseOffset, t1, t2, t3); // Uses $t1=index, result in $t2, uses $t3 as temp

                    // Store value: sw $t0, 0($t2)
                    add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("0", "DEC"), vrAddr));

                    // Increment index: i++
                    add_regular_to_mips(MIPSOp.ADDI, t1, t1, new Imm("1", "DEC")); // Use ADDI

                    add_regular_to_mips(MIPSOp.J, new Addr(loopLabel));
                    add_regular_to_mips(MIPSOp.LABEL, new Addr(endLoopLabel));

                } else {
                     System.err.println("Error: Unsupported number of operands for ASSIGN: " + instruc.operands.length);
                }
                break;
            }


            // --- Arithmetic Ops --- (Using ADD/SUB etc.)
            case ADD:
            case SUB:
            case MULT:
            case DIV:
            case AND:
            case OR:
            {
                String destName = ((IRVariableOperand) instruc.operands[0]).getName();
                int destOffset = offsets_stack.get(destName);
                IROperand src1Op = instruc.operands[1];
                IROperand src2Op = instruc.operands[2];

                load_operand_to_physical_reg(src1Op, t0); // $t0 = src1
                load_operand_to_physical_reg(src2Op, t1); // $t1 = src2

                MIPSOp mipsOp = get_binary_op(instruc.opCode);
                if (mipsOp != null) {
                    boolean handled_imm = false;
                    // Handle immediate versions explicitly
                    if (src2Op instanceof IRConstantOperand) {
                        Imm immOperand = new Imm(((IRConstantOperand) src2Op).getValueString(), "DEC");
                        if (instruc.opCode == IRInstruction.OpCode.ADD) {
                            add_regular_to_mips(MIPSOp.ADDI, t0, t0, immOperand); // Use ADDI
                            handled_imm = true;
                        } else if (instruc.opCode == IRInstruction.OpCode.SUB) {
                             // Still need negation for SUB Immediate
                             try {
                                 int intVal = immOperand.getInt();
                                 add_regular_to_mips(MIPSOp.ADDI, t0, t0, new Imm(String.valueOf(-intVal), "DEC")); // Use ADDI
                                 handled_imm = true;
                             } catch (NumberFormatException e) { /* Fall through */ }
                        } else if (instruc.opCode == IRInstruction.OpCode.AND) {
                            add_regular_to_mips(MIPSOp.ANDI, t0, t0, immOperand);
                            handled_imm = true;
                        } else if (instruc.opCode == IRInstruction.OpCode.OR) {
                            add_regular_to_mips(MIPSOp.ORI, t0, t0, immOperand);
                            handled_imm = true;
                        }
                    }

                    if (!handled_imm) {
                         // General R-type case or MUL/DIV pseudo-ops
                         add_regular_to_mips(mipsOp, t0, t0, t1); // e.g., ADD $t0, $t0, $t1 or MUL $t0, $t0, $t1
                         // NO MFLO needed after DIV pseudo-op
                    }
                } else {
                     System.err.println("Error: MIPS op mapping not found for " + instruc.opCode);
                     break;
                }

                // Store result
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + destOffset, "DEC"), fp));
                break;
            }


            // --- Branching --- (Mostly same, relies on pseudo-ops BLT, BGT, BGE)
            case BREQ:
            case BRNEQ:
            case BRLT:
            case BRGT:
            case BRGEQ:
            {
                String b_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String b_unique_label = func.name + "_" + b_label_name;
                Addr labelAddr = new Addr(b_unique_label);
                IROperand op1 = instruc.operands[1];
                IROperand op2 = instruc.operands[2];

                load_operand_to_physical_reg(op1, t0); // $t0 = op1
                load_operand_to_physical_reg(op2, t1); // $t1 = op2

                MIPSOp branchOp = get_branch_op(instruc.opCode);
                if (branchOp != null) {
                    add_regular_to_mips(branchOp, labelAddr, t0, t1);
                } else {
                     System.err.println("Error: MIPS branch op mapping not found for " + instruc.opCode);
                }
                break;
            }

            case GOTO:
                String g_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String g_unique_label = func.name + "_" + g_label_name;
                add_regular_to_mips(MIPSOp.J, new Addr(g_unique_label));
                break;

            case RETURN: {
                if (instruc.operands.length > 0) {
                    load_operand_to_physical_reg(instruc.operands[0], v0); // $v0 = return_value
                }

                // Epilogue
                if (current_frame_size > 0) {
                    // Restore $ra
                    if (current_is_non_leaf) {
                        int ra_save_offset = 4; // Relative to $fp
                        add_regular_to_mips(MIPSOp.LW, ra, new Addr(new Imm("" + ra_save_offset, "DEC"), fp));
                    }
                    // Restore old $fp
                    int fp_save_offset = 0; // Relative to $fp
                    add_regular_to_mips(MIPSOp.LW, fp, new Addr(new Imm("" + fp_save_offset, "DEC"), fp));
                    // Deallocate frame
                    add_regular_to_mips(MIPSOp.ADDI, sp, sp, new Imm("" + current_frame_size, "DEC")); // Use ADDI
                }
                add_regular_to_mips(MIPSOp.JR, ra); // Use JR
                break;
            } // End RETURN

            case CALL:
            case CALLR: {
                String func_name = "";
                int first_arg_ir_index = -1;
                IROperand destOp = null;
                IROperand funcTargetOp = null;
                boolean isCallrVar = false;

                if (instruc.opCode == IRInstruction.OpCode.CALL) {
                    funcTargetOp = instruc.operands[0];
                    func_name = ((IRFunctionOperand) funcTargetOp).getName();
                    first_arg_ir_index = 1;
                } else { // CALLR
                    destOp = instruc.operands[0];
                    funcTargetOp = instruc.operands[1];
                    if (funcTargetOp instanceof IRFunctionOperand) {
                        func_name = ((IRFunctionOperand) funcTargetOp).getName();
                    } else { // Variable target
                        isCallrVar = true;
                        // Use variable name for intrinsic check, actual address loaded later
                        func_name = ((IRVariableOperand) funcTargetOp).getName();
                    }
                    first_arg_ir_index = 2;
                }

                if (is_intrinsic(func_name)) {
                     handle_intrinsic_call(instruc, func_name, first_arg_ir_index, destOp);
                } else {
                    // Standard Function Call
                    int num_args = instruc.operands.length - first_arg_ir_index;

                    // 1. Setup arguments (same as before)
                    for (int i = 0; i < num_args; i++) {
                        IROperand argOp = instruc.operands[first_arg_ir_index + i];
                        load_operand_to_physical_reg(argOp, t0); // $t0 = arg value
                        if (i < 4) {
                            Register dest_arg_reg = get_arg_register(i);
                            add_regular_to_mips(MIPSOp.MOVE, dest_arg_reg, t0);
                        } else {
                            int outgoing_sp_offset = i * WORD_SIZE;
                            add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + outgoing_sp_offset, "DEC"), sp));
                        }
                    }

                    // 2. Make the call
                    if (instruc.opCode == IRInstruction.OpCode.CALL || !isCallrVar) {
                        // Direct call using JAL
                        add_regular_to_mips(MIPSOp.JAL, new Addr(func_name));
                    } else { // CALLR via variable - Use JR workaround
                        // Load function address from variable's stack slot into $t1
                        load_operand_to_physical_reg(funcTargetOp, t1); // $t1 = function address

                        // Manually save return address
                        String returnLabel = get_unique_label("callr_return");
                        add_regular_to_mips(MIPSOp.LA, ra, new Addr(returnLabel)); // $ra = address of returnLabel

                        // Jump using JR
                        add_regular_to_mips(MIPSOp.JR, t1); // jr $t1

                        // Define the return label immediately after
                        add_regular_to_mips(MIPSOp.LABEL, new Addr(returnLabel));
                    }

                    // 3. Retrieve return value for CALLR
                    if (instruc.opCode == IRInstruction.OpCode.CALLR && destOp instanceof IRVariableOperand) {
                        String destName = ((IRVariableOperand) destOp).getName();
                        int destOffset = offsets_stack.get(destName);
                        add_regular_to_mips(MIPSOp.SW, v0, new Addr(new Imm("" + destOffset, "DEC"), fp)); // Store $v0 result
                    }
                } // End standard call
                break;
            } // End CALL/CALLR

             // --- Array Operations --- (Mostly same, use ADD/ADDI)
            case ARRAY_STORE: {
                IROperand srcOp = instruc.operands[0];
                IRVariableOperand arrayVar = (IRVariableOperand) instruc.operands[1];
                IROperand indexOp = instruc.operands[2];

                load_operand_to_physical_reg(srcOp, t0);   // $t0 = value to store
                load_operand_to_physical_reg(indexOp, t1); // $t1 = index

                int arrayBaseOffset = offsets_stack.get(arrayVar.getName());
                Register vrAddr = calculate_array_address(arrayBaseOffset, t1, t2, t3); // Address in $t2

                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("0", "DEC"), vrAddr)); // Store value
                break;
            }

            case ARRAY_LOAD: {
                IRVariableOperand destVar = (IRVariableOperand) instruc.operands[0];
                IRVariableOperand arrayVar = (IRVariableOperand) instruc.operands[1];
                IROperand indexOp = instruc.operands[2];

                String destName = destVar.getName();
                int destOffset = offsets_stack.get(destName);

                load_operand_to_physical_reg(indexOp, t0); // $t0 = index

                int arrayBaseOffset = offsets_stack.get(arrayVar.getName());
                Register vrAddr = calculate_array_address(arrayBaseOffset, t0, t1, t2); // Address in $t1

                add_regular_to_mips(MIPSOp.LW, t0, new Addr(new Imm("0", "DEC"), vrAddr)); // Load value into $t0

                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + destOffset, "DEC"), fp)); // Store to dest slot
                break;
            }

            default:
                 System.err.println("Warning: Opcode not handled: " + instruc.opCode);
                 break;
        } // End switch
    } // End translate_ir_to_mips


    // --- Helper Methods --- (Adjusted for ADD/ADDI)

    // Helper to get MIPS binary operation (uses ADD, SUB now)
    private MIPSOp get_binary_op(IRInstruction.OpCode opCode) {
        switch (opCode) {
            case ADD: return MIPSOp.ADD; // Use ADD (check overflow?)
            case SUB: return MIPSOp.SUB; // Use SUB (check overflow?)
            case MULT: return MIPSOp.MUL;
            case DIV: return MIPSOp.DIV;
            case AND: return MIPSOp.AND;
            case OR: return MIPSOp.OR;
            default: return null;
        }
    }

    // get_branch_op remains the same
     private MIPSOp get_branch_op(IRInstruction.OpCode opCode) {
        switch (opCode) {
            case BREQ: return MIPSOp.BEQ;
            case BRNEQ: return MIPSOp.BNE;
            case BRLT: return MIPSOp.BLT;   // Pseudo-op
            case BRGT: return MIPSOp.BGT;   // Pseudo-op
            case BRGEQ: return MIPSOp.BGE;  // Pseudo-op
            default: return null;
        }
    }

    // get_arg_register remains the same
    private Register get_arg_register(int index) {
        if (index == 0) return a0;
        if (index == 1) return a1;
        if (index == 2) return a2;
        if (index == 3) return a3;
        return null;
    }

     // is_intrinsic remains the same
     private boolean is_intrinsic(String func_name) {
         if (func_name == null) return false;
         switch (func_name) {
             case "geti": case "getc": case "puti": case "putc":
                 return true;
             default:
                 return false;
         }
     }

     // handle_intrinsic_call remains the same
     private void handle_intrinsic_call(IRInstruction instruc, String func_name, int first_arg_ir_index, IROperand destOp) {
         switch (func_name) {
             case "geti": // read_int: code 5, returns in $v0
                 add_regular_to_mips(MIPSOp.LI, v0, new Imm("5", "DEC"));
                 add_regular_to_mips(MIPSOp.SYSCALL);
                 if (destOp instanceof IRVariableOperand) {
                     String destName = ((IRVariableOperand) destOp).getName();
                     int destOffset = offsets_stack.get(destName);
                     add_regular_to_mips(MIPSOp.SW, v0, new Addr(new Imm("" + destOffset, "DEC"), fp));
                 }
                 break;
             case "getc": // read_char: code 12, result in v0 (as per SPIM syscall list A.9.1)
                 add_regular_to_mips(MIPSOp.LI, v0, new Imm("12", "DEC"));
                 add_regular_to_mips(MIPSOp.SYSCALL);
                 // Result is in v0 according to Figure A.9.1, not a0
                 if (destOp instanceof IRVariableOperand) {
                     String destName = ((IRVariableOperand) destOp).getName();
                     int destOffset = offsets_stack.get(destName);
                     add_regular_to_mips(MIPSOp.SW, v0, new Addr(new Imm("" + destOffset, "DEC"), fp));
                 }
                 break;
             case "puti": // print_int: code 1, arg in $a0
                 if (instruc.operands.length > first_arg_ir_index) {
                     load_operand_to_physical_reg(instruc.operands[first_arg_ir_index], a0);
                     add_regular_to_mips(MIPSOp.LI, v0, new Imm("1", "DEC"));
                     add_regular_to_mips(MIPSOp.SYSCALL);
                 } else { System.err.println("Error: Missing argument for puti"); }
                 break;
             case "putc": // print_char: code 11, arg in $a0
                 if (instruc.operands.length > first_arg_ir_index) {
                     load_operand_to_physical_reg(instruc.operands[first_arg_ir_index], a0);
                     add_regular_to_mips(MIPSOp.LI, v0, new Imm("11", "DEC"));
                     add_regular_to_mips(MIPSOp.SYSCALL);
                 } else { System.err.println("Error: Missing argument for putc"); }
                 break;
             default:
                 System.err.println("Error: Intrinsic function handler not implemented for: " + func_name);
         }
     }

    // load_operand_to_physical_reg remains the same
    private void load_operand_to_physical_reg(IROperand op, Register targetReg) {
        if (op instanceof IRConstantOperand) {
            String value = ((IRConstantOperand) op).getValueString();
            add_regular_to_mips(MIPSOp.LI, targetReg, new Imm(value, "DEC"));
        } else if (op instanceof IRVariableOperand) {
            String name = ((IRVariableOperand) op).getName();
            if (!offsets_stack.containsKey(name)) {
                 System.err.println("CRITICAL ERROR: Stack offset not found for variable: " + name + " (Operand: " + op + ")");
                 add_regular_to_mips(MIPSOp.LI, targetReg, new Imm("0", "DEC")); // Fallback
                 return;
             }
            int offset = offsets_stack.get(name);
            add_regular_to_mips(MIPSOp.LW, targetReg, new Addr(new Imm("" + offset, "DEC"), fp));
        } else if (op instanceof IRLabelOperand || op instanceof IRFunctionOperand) {
             String labelName = (op instanceof IRLabelOperand) ? ((IRLabelOperand)op).getName() : ((IRFunctionOperand)op).getName();
             add_regular_to_mips(MIPSOp.LA, targetReg, new Addr(labelName));
        } else {
             System.err.println("Error: Unsupported operand type in load_operand_to_physical_reg: " + op.getClass().getSimpleName());
             add_regular_to_mips(MIPSOp.LI, targetReg, new Imm("0", "DEC")); // Fallback
        }
    }

     // get_mips_operand remains the same
     private MIPSOperand get_mips_operand(IROperand op) {
         // ... (same as before) ...
          if (op instanceof IRConstantOperand) {
             return new Imm(((IRConstantOperand) op).getValueString(), "DEC");
         } else if (op instanceof IRVariableOperand) {
             System.err.println("Warning: get_mips_operand called for variable in naive - should load first.");
             return null;
         } else if (op instanceof IRLabelOperand) {
             return new Addr(((IRLabelOperand) op).getName());
         } else if (op instanceof IRFunctionOperand) {
             return new Addr(((IRFunctionOperand) op).getName());
         }
         return null;
     }

    // calculate_array_address uses ADDI/ADD now
    private Register calculate_array_address(int baseOffset, Register regIndex, Register regAddr, Register regTemp) {
        // regTemp = regIndex * 4
        add_regular_to_mips(MIPSOp.SLL, regTemp, regIndex, new Imm("2", "DEC"));
        // regAddr = $fp + baseOffset
        add_regular_to_mips(MIPSOp.ADDI, regAddr, fp, new Imm("" + baseOffset, "DEC")); // Use ADDI
        // regAddr = regAddr + regTemp
        add_regular_to_mips(MIPSOp.ADD, regAddr, regAddr, regTemp); // Use ADD
        return regAddr;
    }


    // --- add_regular_to_mips overloads --- (Ensure JR overload is correct)
    public void add_regular_to_mips(MIPSOp op, String label, MIPSOperand... operands) {
        MIPSInstruction instruc = new MIPSInstruction(op, label, operands);
        // Add this line for debugging:
        System.out.println("DEBUG: Adding MIPS - " + instruc.toString());
        mips_program.add_instruction(new MIPSInstruction(op, label, operands));
    }
    public void add_regular_to_mips(MIPSOp op, MIPSOperand... operands) {
        //System.out.println("DEBUG: Adding MIPS -");
        add_regular_to_mips(op, null, operands);
    }
     public void add_regular_to_mips(MIPSOp op, Addr label_addr) { // For J, JAL, LABEL, LA $ra, label
        //System.out.println("DEBUG: Adding MIPS - ");
        if (op == MIPSOp.LABEL) {
             add_regular_to_mips(op, label_addr.toString(), new MIPSOperand[0]);
        } else if (op == MIPSOp.LA) { // Handle LA $ra, label case specifically if needed
             // Assuming MIPSInstruction handles LA Reg, Addr
             // This overload might conflict if LA needs a register arg
             System.err.println("Warning: Addr-only overload used for LA. Ensure MIPSInstruction handles this or use LA overload.");
             add_regular_to_mips(op, null, label_addr); // This might be wrong, depends on MIPSInstruction constructor
        }
        else { // J, JAL
            add_regular_to_mips(op, (MIPSOperand)label_addr);
        }
    }
    // Overload for LA Rdest, Addr
    public void add_regular_to_mips(MIPSOp op, Register rDest, Addr label_addr) {
        //System.out.println("DEBUG: Adding MIPS - ");
        add_regular_to_mips(op, (MIPSOperand)rDest, (MIPSOperand)label_addr);
    }

    // Branch overload (label, r1, r2)
    public void add_regular_to_mips(MIPSOp op, Addr label, Register r1, Register r2) {
        //System.out.println("DEBUG: Adding MIPS - ");
        add_regular_to_mips(op, r1, r2, label);
    }
    // Branch overload (label, r1, imm)
    public void add_regular_to_mips(MIPSOp op, Addr label, Register r1, Imm imm) {
        //System.out.println("DEBUG: Adding MIPS - ");
         // Assuming MIPSInstruction handles op, r1, imm, label for pseudo-ops
          if (op == MIPSOp.BGE) { // Extend for other immediate branches if needed
             add_regular_to_mips(op, r1, imm, label);
         } else {
              System.err.println("Warning: Immediate branch format used for non-supported op: " + op);
         }
    }
     // Overload for JR Rtarget
     public void add_regular_to_mips(MIPSOp op, Register targetReg) {
        //System.out.println("DEBUG: Adding MIPS - ");
         if (op == MIPSOp.JR) {
             add_regular_to_mips(op, (MIPSOperand) targetReg);
         } else {
             System.err.println("Warning: Single register overload used for non-JR op: " + op);
             add_regular_to_mips(op, (MIPSOperand) targetReg);
         }
     }
     // Removed MFLO/MFHI overload as they are not available


} // End class