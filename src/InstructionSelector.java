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

    private static final int WORD_SIZE = 4;

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

    public int current_frame_size;
    public boolean current_is_non_leaf;

    // Helper to generate unique label names
    private String get_unique_label(String prefix) {
        return prefix + "_" + labelCounter.getAndIncrement();
    }

    public MIPSProgram select_instructions(IRProgram ir_program) {
        if (ir_program == null) {
            System.out.println("there is a NULLLLL program!!!!!");
            return null;
        }
        System.out.println("HELLLO ARE WE INSIDE OF SELECT_INSTRUCTIONS????");
        mips_program = new MIPSProgram();
        System.out.println("ARE WE PASSED THIS??");

        // we make sure that execution always begins at main because before it was starting at quicksort (way file is written)
        add_regular_to_mips(MIPSOp.J, new Addr("main"));

        for (IRFunction function : ir_program.functions) {
            System.out.println("\nwe are inside the LOOP\n");
            translate_function(function);
        }
        return mips_program;
    }

    public void translate_function(IRFunction function) {
        System.out.println("HELLO I HAVE ENTERED THE FUNCTION WOOWOHOOO.");
        offsets_stack = new HashMap<>(); // for every function we want to have a new mapping for its own stack frame
        current_is_non_leaf = false; // always reset this to false for every function (global variable)
        int max_num_args_called = 0;

        // let's see if our function is a non leaf ==> if it calls another function then it is not a leaf
            // ==> we do this to see if we need to allocate space for the return address or not!!!
        for (IRInstruction instruc : function.instructions) {
            if (instruc.opCode == IRInstruction.OpCode.CALL) {
                current_is_non_leaf = true;
            } else if (instruc.opCode == IRInstruction.OpCode.CALLR) {
                current_is_non_leaf = true;
            }
        }

        // let's see how many arugments we need (maximum number of them)
            // ==> we look through all the instructions and find all the CALL and CALLR isntructions and find the maximum number of operands present
        for (IRInstruction instruc : function.instructions) {
            if (instruc.opCode == IRInstruction.OpCode.CALL) {
                int num_operands = instruc.operands.length;
                String func_name = ((IRFunctionOperand) instruc.operands[0]).getName();
                if (!(func_name.equals("geti") || func_name.equals("getc") || func_name.equals("puti") || func_name.equals("putc"))) { // Check only for non-intrinsics
                     int num_args = num_operands - 1;
                     max_num_args_called = Math.max(max_num_args_called, num_args);
                }
            } else if (instruc.opCode == IRInstruction.OpCode.CALLR) {
                String func_name = "";
                int num_operands = instruc.operands.length;
                IROperand funcTargetOp = instruc.operands[1];
                if (funcTargetOp instanceof IRFunctionOperand) {
                    func_name = ((IRFunctionOperand) funcTargetOp).getName();
                } // Else it's a variable, check name later if needed for intrinsics
                if (!(func_name.equals("geti") || func_name.equals("getc") || func_name.equals("puti") || func_name.equals("putc"))) { // Check only for non-intrinsics
                     int num_args = num_operands - 2;
                     max_num_args_called = Math.max(max_num_args_called, num_args);
                }
            }
        }


        // now let's set up our stack frame
        // outgoing args
        int outgoing_arg_bytes = Math.max(4, max_num_args_called) * WORD_SIZE;

        // local vars (regular and arrays)
        int local_var_bytes = 0;
        for (IRVariableOperand var : function.variables) {
            if (var.type instanceof IRArrayType) {
                local_var_bytes+=((IRArrayType) var.type).getSize() * WORD_SIZE;
            } else {
                local_var_bytes+=WORD_SIZE;
            }
        }

        // space to save incoming argument registers (if needed - $a0-$a3)
        int saved_args_bytes = 0;
         for(int i = 0; i < function.parameters.size() && i < 4; i++) {
            saved_args_bytes += WORD_SIZE;
        }

        // Space for saving $ra and $fp (on stack)
        int saved_ra_fp_bytes = 0;
        boolean needs_frame = saved_args_bytes > 0 || local_var_bytes > 0 || outgoing_arg_bytes > 0 || current_is_non_leaf;
        if (needs_frame) {
            saved_ra_fp_bytes += WORD_SIZE; // we will always allocate space for the $fp
             if (current_is_non_leaf) {
                saved_ra_fp_bytes += WORD_SIZE; // if we call another function then we want to allocate space for the return address
            }
        }

        // total size of the stack frame
        int preliminary_size = outgoing_arg_bytes + local_var_bytes + saved_args_bytes + saved_ra_fp_bytes;
        current_frame_size = preliminary_size;
        // this is to align it --> 8 bytes according to MIPS documents
        if (current_frame_size > 0 && current_frame_size % 8 != 0) {
            current_frame_size += WORD_SIZE;
        }

        // function name like is listed in the output files for testing
        add_regular_to_mips(MIPSOp.LABEL, new Addr(function.name));

        if (current_frame_size > 0) {
            // 1. Allocate frame
            add_regular_to_mips(MIPSOp.ADDI, sp, sp, new Imm("-" + current_frame_size, "DEC")); // this is to setup the new stack frame ($sp will now point to the very top of the newly created frame)

            int current_save_offset = current_frame_size; // Relative to new $sp

            // save the return address stored in $ra if we are a non-leaf function
            if (current_is_non_leaf) {
                current_save_offset -= WORD_SIZE;
                add_regular_to_mips(MIPSOp.SW, ra, new Addr(new Imm("" + current_save_offset, "DEC"), sp));
                offsets_stack.put("$ra_save_slot", current_save_offset);
            }

            // always save the old $fp
            current_save_offset -= WORD_SIZE;
            add_regular_to_mips(MIPSOp.SW, fp, new Addr(new Imm("" + current_save_offset, "DEC"), sp));
            offsets_stack.put("$fp_save_slot", current_save_offset);

            // set the new $fp to point above the stored return address and frame pointer
            add_regular_to_mips(MIPSOp.ADDI, fp, sp, new Imm("" + current_save_offset, "DEC")); // this is because we aren't sure if we always allocate space for $ra or not so put above so don't worry about it!

            int current_offset = 0; // now we offset from the $fp

            // Map and Save incoming $a0-$a3
             for (int i = 0; i < function.parameters.size() && i < 4; i++) {
                 current_offset -= WORD_SIZE;
                 Register arg_reg = get_arg_register(i);
                 add_regular_to_mips(MIPSOp.SW, arg_reg, new Addr(new Imm("" + current_offset, "DEC"), fp));
                 offsets_stack.put(function.parameters.get(i).getName(), current_offset);
            }

            // Map Local Variables & Temporaries
            for (IRVariableOperand var : function.variables) {
                int size;
                if (var.type instanceof IRArrayType) {
                    size = ((IRArrayType) var.type).getSize() * WORD_SIZE;
                } else {
                    size = WORD_SIZE;
                }
                current_offset -= size;
                offsets_stack.put(var.getName(), current_offset);
            }

            // Map Incoming Arguments 5+
            int arg5_base_offset;
            if (current_is_non_leaf) {
                arg5_base_offset = 8; // we have to account for the $ra being there
            } else {
                arg5_base_offset = 4; // else there is no $ra slot so we are -4 less
            }
            for (int i = 4; i < function.parameters.size(); i++) {
                int arg_fp_offset = arg5_base_offset + (i - 4) * WORD_SIZE;
                offsets_stack.put(function.parameters.get(i).getName(), arg_fp_offset);
            }
        }

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
    }


    public void translate_ir_to_mips(IRInstruction instruc, IRFunction func) {
        switch(instruc.opCode) {
            case LABEL:
                String l_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String l_unique_label = func.name + "_" + l_label_name;
                add_regular_to_mips(MIPSOp.LABEL, new Addr(l_unique_label));
                break;
            case ASSIGN: { // assign, dest, src OR assign, array, size, value
                if (instruc.operands.length == 2) {
                    // assign, destVar, src
                    IROperand dest = instruc.operands[0];
                    IROperand src = instruc.operands[1];
                    // ... (rest is same as previous version) ...
                    if (!(dest instanceof IRVariableOperand)) {
                        System.err.println("Error: Destination of ASSIGN must be a variable/temp.");
                        break;
                    }
                    String dest_name = ((IRVariableOperand) dest).getName();
                    int dest_offset = offsets_stack.get(dest_name);
                    load_operand_to_physical_reg(src, t0, func); // $t0 = src
                    add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + dest_offset, "DEC"), fp)); // Mem[fp+destOffset] = $t0

                } else if (instruc.operands.length == 3) {
                    // assign, arrayName, size, value
                    // this is for ARRAY INITIALIZATION!!!!!!!!! ==> must allocate space on the stack
                    IROperand array_op = instruc.operands[0];
                    IROperand size_op = instruc.operands[1];
                    IROperand value_op = instruc.operands[2];

                    String array_name = ((IRVariableOperand) array_op).getName();
                    int array_base_offset = offsets_stack.get(array_name);
                    int size = Integer.parseInt(((IRConstantOperand) size_op).getValueString());

                    load_operand_to_physical_reg(value_op, t0, func); // $t0 = value
                    add_regular_to_mips(MIPSOp.LI, t1, new Imm("0", "DEC")); // $t1 = 0 (i=0)

                    String loop_label = get_unique_label("array_init_loop");
                    String end_loop_label = get_unique_label("array_init_end");

                    add_regular_to_mips(MIPSOp.LABEL, new Addr(loop_label));
                    // Use BGE pseudo-op for check: if $t1 >= size, goto endLoopLabel
                    // Need size in a register first ($t3)
                    add_regular_to_mips(MIPSOp.LI, t3, new Imm("" + size, "DEC"));
                    add_regular_to_mips(MIPSOp.BGE, new Addr(end_loop_label), t1, t3);

                    // Calculate address into $t2
                    Register addr = calculate_array_address(array_base_offset, t1, t2, t3); // Uses $t1=index, result in $t2, uses $t3 as temp

                    // Store value: sw $t0, 0($t2)
                    add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("0", "DEC"), addr));

                    // Increment index: i++
                    add_regular_to_mips(MIPSOp.ADDI, t1, t1, new Imm("1", "DEC")); // Use ADDI

                    add_regular_to_mips(MIPSOp.J, new Addr(loop_label));
                    add_regular_to_mips(MIPSOp.LABEL, new Addr(end_loop_label));

                }
                break;
            }

            case ADD:
                String add_destName = ((IRVariableOperand) instruc.operands[0]).getName();
                int add_destOffset = offsets_stack.get(add_destName);
                IROperand add_src1Op = instruc.operands[1];
                IROperand add_src2Op = instruc.operands[2];

                load_operand_to_physical_reg(add_src1Op, t0, func); // $t0 = src1
                load_operand_to_physical_reg(add_src2Op, t1, func); // $t1 = src2
                if (add_src2Op instanceof IRConstantOperand) {
                    Imm immOperand = new Imm(((IRConstantOperand) add_src2Op).getValueString(), "DEC");
                    add_regular_to_mips(MIPSOp.ADDI, t0, t0, immOperand); // Use ADDI
                } else {
                    add_regular_to_mips(MIPSOp.ADD, t0, t0, t1); // e.g., ADD $t0, $t0, $t1 or MUL $t0, $t0, $t1
                }
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + add_destOffset, "DEC"), fp));
                break;
            case SUB:
                String sub_destName = ((IRVariableOperand) instruc.operands[0]).getName();
                int sub_destOffset = offsets_stack.get(sub_destName);
                IROperand sub_src1Op = instruc.operands[1];
                IROperand sub_src2Op = instruc.operands[2];
                load_operand_to_physical_reg(sub_src1Op, t0, func); // $t0 = src1
                load_operand_to_physical_reg(sub_src2Op, t1, func); // $t1 = src2
                if (sub_src2Op instanceof IRConstantOperand) {
                    Imm immOperand = new Imm(((IRConstantOperand) sub_src2Op).getValueString(), "DEC");
                    add_regular_to_mips(MIPSOp.ADDI, t0, t0, new Imm("-" + immOperand.toString(), "DEC")); // Use ADDI
                } else {
                    add_regular_to_mips(MIPSOp.SUB, t0, t0, t1); // e.g., ADD $t0, $t0, $t1 or MUL $t0, $t0, $t1
                }
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + sub_destOffset, "DEC"), fp));
                break;
            case MULT:
                String mult_destName = ((IRVariableOperand) instruc.operands[0]).getName();
                int mult_destOffset = offsets_stack.get(mult_destName);
                IROperand mult_src1Op = instruc.operands[1];
                IROperand mult_src2Op = instruc.operands[2];

                load_operand_to_physical_reg(mult_src1Op, t0, func); // $t0 = src1
                load_operand_to_physical_reg(mult_src2Op, t1, func); // $t1 = src2

                add_regular_to_mips(MIPSOp.MUL, t0, t0, t1); 
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + mult_destOffset, "DEC"), fp));
                break;
            case DIV:
                String div_destName = ((IRVariableOperand) instruc.operands[0]).getName();
                int div_destOffset = offsets_stack.get(div_destName);
                IROperand div_src1Op = instruc.operands[1];
                IROperand div_src2Op = instruc.operands[2];

                load_operand_to_physical_reg(div_src1Op, t0, func); // $t0 = src1
                load_operand_to_physical_reg(div_src2Op, t1, func); // $t1 = src2

                add_regular_to_mips(MIPSOp.DIV, t0, t0, t1);
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + div_destOffset, "DEC"), fp));
                break;
            case AND:
                String and_destName = ((IRVariableOperand) instruc.operands[0]).getName();
                int and_destOffset = offsets_stack.get(and_destName);
                IROperand and_src1Op = instruc.operands[1];
                IROperand and_src2Op = instruc.operands[2];

                load_operand_to_physical_reg(and_src1Op, t0, func); // $t0 = src1
                load_operand_to_physical_reg(and_src2Op, t1, func); // $t1 = src2
                if (and_src2Op instanceof IRConstantOperand) {
                    Imm immOperand = new Imm(((IRConstantOperand) and_src2Op).getValueString(), "DEC");
                    add_regular_to_mips(MIPSOp.ANDI, t0, t0, immOperand);
                } else {
                    add_regular_to_mips(MIPSOp.AND, t0, t0, t1);
                }
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + and_destOffset, "DEC"), fp));
                break;
            case OR:
                String or_destName = ((IRVariableOperand) instruc.operands[0]).getName();
                int or_destOffset = offsets_stack.get(or_destName);
                IROperand or_src1Op = instruc.operands[1];
                IROperand or_src2Op = instruc.operands[2];

                load_operand_to_physical_reg(or_src1Op, t0, func); // $t0 = src1
                load_operand_to_physical_reg(or_src2Op, t1, func); // $t1 = src2
                if (or_src2Op instanceof IRConstantOperand) {
                    Imm immOperand = new Imm(((IRConstantOperand) or_src2Op).getValueString(), "DEC");
                    add_regular_to_mips(MIPSOp.ORI, t0, t0, immOperand);
                } else {
                    add_regular_to_mips(MIPSOp.OR, t0, t0, t1);
                }
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + or_destOffset, "DEC"), fp));
                break;
            case BREQ:
                String breq_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String breq_unique_label = func.name + "_" + breq_label_name;
                Addr breq_label_addr = new Addr(breq_unique_label);
                IROperand breq_op1 = instruc.operands[1];
                IROperand breq_op2 = instruc.operands[2];

                load_operand_to_physical_reg(breq_op1, t0, func); // $t0 = op1
                load_operand_to_physical_reg(breq_op2, t1, func); // $t1 = op2

                add_regular_to_mips(MIPSOp.BEQ, breq_label_addr, t0, t1);
                break;
            case BRNEQ:
                String brneq_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String brneq_unique_label = func.name + "_" + brneq_label_name;
                Addr brneq_label_addr = new Addr(brneq_unique_label);
                IROperand brneq_op1 = instruc.operands[1];
                IROperand brneq_op2 = instruc.operands[2];

                load_operand_to_physical_reg(brneq_op1, t0, func); // $t0 = op1
                load_operand_to_physical_reg(brneq_op2, t1, func); // $t1 = op2

                add_regular_to_mips(MIPSOp.BNE, brneq_label_addr, t0, t1);
                break;
            case BRLT:
                String brlt_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String brlt_unique_label = func.name + "_" + brlt_label_name;
                Addr brlt_label_addr = new Addr(brlt_unique_label);
                IROperand brlt_op1 = instruc.operands[1];
                IROperand brlt_op2 = instruc.operands[2];

                load_operand_to_physical_reg(brlt_op1, t0, func); // $t0 = op1
                load_operand_to_physical_reg(brlt_op2, t1, func); // $t1 = op2

                add_regular_to_mips(MIPSOp.BLT, brlt_label_addr, t0, t1);
                break;
            case BRGT:
                String brgt_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String brgt_unique_label = func.name + "_" + brgt_label_name;
                Addr brgt_label_addr = new Addr(brgt_unique_label);
                IROperand brgt_op1 = instruc.operands[1];
                IROperand brgt_op2 = instruc.operands[2];

                load_operand_to_physical_reg(brgt_op1, t0, func); // $t0 = op1
                load_operand_to_physical_reg(brgt_op2, t1, func); // $t1 = op2

                add_regular_to_mips(MIPSOp.BGT, brgt_label_addr, t0, t1);
                break;
            case BRGEQ:
                String brgeq_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String brgeq_unique_label = func.name + "_" + brgeq_label_name;
                Addr brgeq_label_addr = new Addr(brgeq_unique_label);
                IROperand brgeq_op1 = instruc.operands[1];
                IROperand brgeq_op2 = instruc.operands[2];

                load_operand_to_physical_reg(brgeq_op1, t0, func); // $t0 = op1
                load_operand_to_physical_reg(brgeq_op2, t1, func); // $t1 = op2

                add_regular_to_mips(MIPSOp.BGE, brgeq_label_addr, t0, t1);
                break;

            case GOTO:
                String g_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String g_unique_label = func.name + "_" + g_label_name;
                add_regular_to_mips(MIPSOp.J, new Addr(g_unique_label));
                break;

            case RETURN:
                // original had check if it was main or not, but then realized that if it is, then we would not reach a RETURN instruction ==> moved handling to translate_function end
                if (current_frame_size > 0) {
                    // if we have an $ra slot (value saved)
                    if (current_is_non_leaf) {
                        add_regular_to_mips(MIPSOp.LW, ra, new Addr(new Imm("" + 4, "DEC"), fp));
                    }
                    // let's now restore the prev saved fp
                    int fp_save_offset = 0; // remember our fp is point to the top of this slot so we have no offset
                    add_regular_to_mips(MIPSOp.LW, fp, new Addr(new Imm("" + fp_save_offset, "DEC"), fp));
                    // let's set sp back to caller's stack frame (restore) ==> get rid of this entire current frame
                    add_regular_to_mips(MIPSOp.ADDI, sp, sp, new Imm("" + current_frame_size, "DEC")); 
                }
                // now let's return execution back to the caller
                add_regular_to_mips(MIPSOp.JR, ra);
                break;

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
                } else {
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

                if (func_name.equals("geti") || func_name.equals("getc") || func_name.equals("puti") || func_name.equals("putc")) { // if this is an intrinsic call (predefined from Tiger-IR)
                     handle_intrinsic_function(instruc, func_name, first_arg_ir_index, destOp, func);
                } else {
                    int num_args = instruc.operands.length - first_arg_ir_index;

                    // 1. Setup arguments (same as before)
                    for (int i = 0; i < num_args; i++) {
                        IROperand argOp = instruc.operands[first_arg_ir_index + i];
                        load_operand_to_physical_reg(argOp, t0, func); // $t0 = arg value
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
                        load_operand_to_physical_reg(funcTargetOp, t1, func); // $t1 = function address

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
                IROperand as_src = instruc.operands[0];
                IRVariableOperand as_array_var = (IRVariableOperand) instruc.operands[1];
                IROperand as_index = instruc.operands[2];
                String as_array_name = as_array_var.getName();

                load_operand_to_physical_reg(as_src, t0, func);   // $t0 = value to store
                load_operand_to_physical_reg(as_index, t1, func); // $t1 = index

                Register final_addr;
                boolean is_param = false;
                for (IRVariableOperand param : func.parameters) {
                    if (param.getName().equals(as_array_name)) {
                        is_param = true;
                    }
                }
                if (is_param) {
                    // array_var is param ==> load base address (val)
                    int param_offset = offsets_stack.get(as_array_name);
                    Addr addr = new Addr(new Imm("" + param_offset, "DEC"), fp);
                    add_regular_to_mips(MIPSOp.LW, t2, addr); // let's store base address of the array in $t2
                    add_regular_to_mips(MIPSOp.SLL, t3, t1, new Imm("2", "DEC")); // $t3 = index * 4
                    add_regular_to_mips(MIPSOp.ADD, t2, t2, t3); // $t2 = $t2 + $t3
                    final_addr = t2;
                } else {
                    int array_offset = offsets_stack.get(as_array_name);
                    final_addr = calculate_array_address(array_offset, t1, t2, t3); // Address in $t2
                }

                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("0", "DEC"), final_addr)); // Store value
                break;
            }

            case ARRAY_LOAD: {
                IRVariableOperand al_dest = (IRVariableOperand) instruc.operands[0];
                IRVariableOperand al_array_var = (IRVariableOperand) instruc.operands[1];
                IROperand al_index = instruc.operands[2];
                String al_array_name = al_array_var.getName();

                String al_dest_name = al_dest.getName();
                int al_dest_offset = offsets_stack.get(al_dest_name);

                load_operand_to_physical_reg(al_index, t0, func); // $t0 = index

                Register final_addr;
                boolean is_param = false;
                for (IRVariableOperand param : func.parameters) {
                    if (param.getName().equals(al_array_name)) {
                        is_param = true;
                    }
                }
                if (is_param) {
                    int param_offset = offsets_stack.get(al_array_name);
                    Addr addr = new Addr(new Imm("" + param_offset, "DEC"), fp);
                    add_regular_to_mips(MIPSOp.LW, t1, addr); // let's store base address of the array in $t2
                    add_regular_to_mips(MIPSOp.SLL, t2, t0, new Imm("2", "DEC")); // $t3 = index * 4
                    add_regular_to_mips(MIPSOp.ADD, t1, t1, t2); // $t2 = $t2 + $t3
                    final_addr = t1;
                } else {
                    int array_offset = offsets_stack.get(al_array_name);
                    final_addr = calculate_array_address(array_offset, t0, t1, t2); // Address in $t1
                }
                add_regular_to_mips(MIPSOp.LW, t0, new Addr(new Imm("0", "DEC"), final_addr)); // Load value into $t0
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + al_dest_offset, "DEC"), fp)); // Store to dest slot
                break;
            }
        }
    }

    // get_arg_register remains the same
    private Register get_arg_register(int index) {
        if (index == 0) {
            return a0;
        }
        if (index == 1) {
            return a1;
        }
        if (index == 2) {
            return a2;
        }
        if (index == 3) {
            return a3;
        }
        return null;
    }

     // handle_intrinsic_call remains the same
     private void handle_intrinsic_function(IRInstruction instruc, String func_name, int first_arg_ir_index, IROperand destOp, IRFunction function) {
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
                     load_operand_to_physical_reg(instruc.operands[first_arg_ir_index], a0, function);
                     add_regular_to_mips(MIPSOp.LI, v0, new Imm("1", "DEC"));
                     add_regular_to_mips(MIPSOp.SYSCALL);
                 } else { System.err.println("Error: Missing argument for puti"); }
                 break;
             case "putc": // print_char: code 11, arg in $a0
                 if (instruc.operands.length > first_arg_ir_index) {
                     load_operand_to_physical_reg(instruc.operands[first_arg_ir_index], a0, function);
                     add_regular_to_mips(MIPSOp.LI, v0, new Imm("11", "DEC"));
                     add_regular_to_mips(MIPSOp.SYSCALL);
                 } else { System.err.println("Error: Missing argument for putc"); }
                 break;
         }
     }

    // load_operand_to_physical_reg remains the same
    private void load_operand_to_physical_reg(IROperand op, Register target_reg, IRFunction func) {
        if (op instanceof IRConstantOperand) {
            String value = ((IRConstantOperand) op).getValueString();
            add_regular_to_mips(MIPSOp.LI, target_reg, new Imm(value, "DEC"));

        } else if (op instanceof IRVariableOperand) {
            IRVariableOperand var = (IRVariableOperand) op;
            String name = var.getName();
            
            if (!offsets_stack.containsKey(name)) {
                 System.err.println("CRITICAL ERROR: Stack offset not found for variable: " + name + " (Func: " + func.name + ")");
                 add_regular_to_mips(MIPSOp.LI, target_reg, new Imm("0", "DEC")); // Fallback
                 return;
             }
            int offset = offsets_stack.get(name);

            if (var.type instanceof IRArrayType) {
                // --- THIS IS THE CRITICAL FIX ---
                if (isParameter(func, name)) {
                    // It's an array PARAMETER.
                    // Load its VALUE (which is the base address).
                    add_regular_to_mips(MIPSOp.LW, target_reg, new Addr(new Imm("" + offset, "DEC"), fp));
                } else {
                    // It's a LOCAL array.
                    // Load its ADDRESS.
                    add_regular_to_mips(MIPSOp.ADDI, target_reg, fp, new Imm("" + offset, "DEC"));
                }
            } else {
                // It's a scalar. Load its VALUE.
                add_regular_to_mips(MIPSOp.LW, target_reg, new Addr(new Imm("" + offset, "DEC"), fp));
            }
            
        } else if (op instanceof IRLabelOperand || op instanceof IRFunctionOperand) {
            String label_name;
            if (op instanceof IRLabelOperand) {
                label_name = ((IRLabelOperand)op).getName();
            } else {
                label_name = ((IRFunctionOperand)op).getName();
            }
            add_regular_to_mips(MIPSOp.LA, target_reg, new Addr(label_name));
        }
    }



                    ////// WORKKKKKKK ON THIIIIIIIIIIISSSSSSSSSSSSSSSSSSSSSSSSSSS ////////////////////////////
    private boolean isParameter(IRFunction func, String varName) {
        for (IRVariableOperand param : func.parameters) {
            if (param.getName().equals(varName)) {
                return true;
            }
        }
        return false;
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