import ir.*;
import ir.datatype.IRArrayType;
import ir.operand.*;
import main.java.mips.*;
import main.java.mips.operand.*;
import static main.java.mips.MIPSInstruction.WORD_SIZE;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class InstructionSelector {
    public MIPSProgram mips_program;
    public Map<String, Integer> offsets_stack;
    private static int label_counter = 0;

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

    public int curr_total_frame_size;
    public boolean current_is_non_leaf;

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
        /*
        we want to go through each function and individually add to the predefined program
            keep adding to the program's "instructions" list until we have done all the functions
        REMEMBER:
            - for CALL and CALLR we care about calling convention but for everything else it does not matter
            - for ASSIGN (with arrays (3 args)) we care about allocating space on the stack for the array
        --> APPROACH these with caution and care
        */

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
        int num_operands = 0;
        int max_num_args = 0;
        for (IRInstruction instruc : function.instructions) {
            if (instruc.opCode == IRInstruction.OpCode.CALL) {
                num_operands = instruc.operands.length;
                int num_args = num_operands - 1;
                max_num_args = Math.max(max_num_args, num_args);
            } else if (instruc.opCode == IRInstruction.OpCode.CALLR) {
                num_operands = instruc.operands.length;
                int num_args = num_operands - 2;
                max_num_args = Math.max(max_num_args, num_args);
            }
        }

        // now let's set up our stack frame
        // outgoing args size (this is the max number of arguments we calculated just now)
        int outgoing_arg_bytes = 0;
        if (max_num_args < 4) {
            outgoing_arg_bytes = 4*WORD_SIZE; // we will always allocate space for all 4 regs
        } else {
            outgoing_arg_bytes = max_num_args*WORD_SIZE;
        }

        // now we find the space needed for our local vars (regular and arrays) ==> if array
        int local_var_bytes = 0;
        for (IRVariableOperand var : function.variables) {
            if (var.type instanceof IRArrayType) {
                local_var_bytes+=((IRArrayType) var.type).getSize() * WORD_SIZE;
            } else { // "normal"
                local_var_bytes+=WORD_SIZE;
            }
        }

        // now we need space for the incoming argument registers ($a0-$a3) if we wish to save them on the stack
        int saved_args_bytes = 0;
        for(int i = 0; i < function.parameters.size() && i < 4; i++) {
            saved_args_bytes += WORD_SIZE;
        }

        // space for saving $ra and $fp (on stack)
        // this is the space for $ra and $fp
        // NOTE: we only allocate space for $ra if we call another function
        int saved_ra_fp_bytes = 0;
        curr_total_frame_size = outgoing_arg_bytes + local_var_bytes + saved_args_bytes;
        //boolean needs_frame = saved_args_bytes > 0 || local_var_bytes > 0 || outgoing_arg_bytes > 0 || current_is_non_leaf;
        if (curr_total_frame_size > 0) {
            saved_ra_fp_bytes += WORD_SIZE; // we will always allocate space for the $fp
            if (current_is_non_leaf) { // MUST ALLOCATE space for return address of caller
                saved_ra_fp_bytes += WORD_SIZE; // if we call another function then we want to allocate space for the return address
            }
        }

        // total size of the stack frame
        curr_total_frame_size += saved_ra_fp_bytes;
        // this is to align it --> 8 bytes according to MIPS documents
        if (curr_total_frame_size > 0 && curr_total_frame_size % 8 != 0) {
            curr_total_frame_size += WORD_SIZE;
        }

        // function name like is listed in the output files for testing
        add_regular_to_mips(MIPSOp.LABEL, new Addr(function.name));

        if (curr_total_frame_size > 0) {
            // let's allocate our entire stack frame now
            // we must use offsets relative to stack pointer because we still do not have the correct $fp as we havent
                //... stored it in our stack frame yet
            add_regular_to_mips(MIPSOp.ADDI, sp, sp, new Imm("-" + curr_total_frame_size, "DEC")); // this is to setup the new stack frame ($sp will now point to the very top of the newly created frame)

            int current_save_offset = curr_total_frame_size; // Relative to new $sp

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

            // let's map the argument registers as we save them on the stack (we will always do this)
            for (int i = 0; i < function.parameters.size() && i < 4; i++) {
                current_offset -= WORD_SIZE;
                Register arg_reg = get_arg_register(i);
                add_regular_to_mips(MIPSOp.SW, arg_reg, new Addr(new Imm("" + current_offset, "DEC"), fp));
                offsets_stack.put(function.parameters.get(i).getName(), current_offset);
            }

            // let's map local variables
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

            // now let's map the number >4 arguments
            int extra_args_offset;
            if (current_is_non_leaf) {
                extra_args_offset = 8; // we have to account for the $ra being there
            } else {
                extra_args_offset = 4; // else there is no $ra slot so we are -4 less
            }
            for (int i = 4; i < function.parameters.size(); i++) {
                int arg_offset_to_fp = extra_args_offset + (i - 4) * WORD_SIZE;
                offsets_stack.put(function.parameters.get(i).getName(), arg_offset_to_fp);
            }
        }

        for (IRInstruction instruc : function.instructions) {
            translate_ir_to_mips(instruc, function);
        }
        
        // WE NEED THIS BECAUSE FUNCTIONS THAT MAY NOT HAVE ANY RETURN STATEMENT may execute
        if (function.name.equals("main")) {
            add_regular_to_mips(MIPSOp.LI, v0, new Imm("10", "DEC")); // code 10 = exit
            add_regular_to_mips(MIPSOp.SYSCALL);
        } else {
            // if there is no return statement inside the function then the case will never hit SO WE NEED THIS!!!!!
            if (curr_total_frame_size > 0) {
                // let's restore the return address if we have one to restore and then restore our $fp
                if (current_is_non_leaf) {
                    add_regular_to_mips(MIPSOp.LW, ra, new Addr(new Imm("4", "DEC"), fp));
                }
                add_regular_to_mips(MIPSOp.LW, fp, new Addr(new Imm("0", "DEC"), fp));       
                // now let's deconstruct our stack frame and set stack pointer to our caller's top of the frame
                add_regular_to_mips(MIPSOp.ADDI, sp, sp, new Imm("" + curr_total_frame_size, "DEC")); 
            }
            add_regular_to_mips(MIPSOp.JR, ra);
        }
    }


    public void translate_ir_to_mips(IRInstruction instruc, IRFunction func) {
        switch(instruc.opCode) {
            case LABEL:
                String l_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String l_unique_label = func.name + "_" + l_label_name;
                add_regular_to_mips(MIPSOp.LABEL, new Addr(l_unique_label));
                break;
            case ASSIGN: // assign, dest, src OR assign, array, size, value (these are the different uses of ASSIGN (2 = simple, 3 = complex))
                if (instruc.operands.length == 2) {
                    // assign, destVar, src
                    IROperand dest = instruc.operands[0];
                    IROperand src = instruc.operands[1];
                    if (!(dest instanceof IRVariableOperand)) {
                        break;
                    }
                    String dest_name = ((IRVariableOperand) dest).getName();
                    int dest_offset = offsets_stack.get(dest_name);
                    load_operand_to_physical_reg(src, t0, func);
                    Addr store_location = new Addr(new Imm("" + dest_offset, "DEC"), fp);
                    add_regular_to_mips(MIPSOp.SW, t0, store_location);

                } // we have an array !!! SPECIAL CASE !!!!!!!
                    /* 
                     op, x , size, value
                     i.e. if ASSIGN, X, 100, 10:
                        ==> CODE:
                                type ArrayInt = array[100] of int;
                                var X : ArrayInt := 10;
                            10 is the value we assign to all elements of array X
                            and we have 100 elements
                        op, x, size, value
                            x: variable that will hold the array
                            size: # of elements the array has
                            value: the value for each element of the array when initialized
                     */ 
                    else if (instruc.operands.length == 3) {
                    // assign, arrayName, size, value
                    // this is for ARRAY INITIALIZATION!!!!!!!!! ==> must allocate space on the stack
                    IROperand array_op = instruc.operands[0];
                    IROperand size_op = instruc.operands[1];
                    IROperand value_op = instruc.operands[2];

                    String array_name = ((IRVariableOperand) array_op).getName();
                    int array_base_offset = offsets_stack.get(array_name);
                    String str_size = ((IRConstantOperand) size_op).getValueString();
                    int size = Integer.parseInt(str_size);

                    load_operand_to_physical_reg(value_op, t0, func); // NOTE: $t0 is our value
                    add_regular_to_mips(MIPSOp.LI, t1, new Imm("0", "DEC"));

                    String loop_label = get_unique_label("array_init_loop");
                    String end_loop_label = get_unique_label("array_init_end");

                    add_regular_to_mips(MIPSOp.LABEL, new Addr(loop_label));
                    add_regular_to_mips(MIPSOp.LI, t3, new Imm("" + size, "DEC"));
                    add_regular_to_mips(MIPSOp.BGE, new Addr(end_loop_label), t1, t3);

                    // here we will calculate the address and store into $t2
                    Register addr = calculate_array_address(array_base_offset, t1, t2, t3); //NOTE: $t1 is index, $t2 is addr, and $t3 is temp for temp storage for the function call

                    // now we will store the value into the memory location of addr ==> value comes from $t0
                    add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("0", "DEC"), addr));
                    add_regular_to_mips(MIPSOp.ADDI, t1, t1, new Imm("1", "DEC")); //$t1 is our index so let's increment and store it

                    add_regular_to_mips(MIPSOp.J, new Addr(loop_label));
                    add_regular_to_mips(MIPSOp.LABEL, new Addr(end_loop_label));

                }
                break;

            // NOTE: that ADD and all these arithmetic and binary operations will look relatively the same because of their nature, operand order
            //these are all arithmetic operations
            /* for add, and, or ==> check if any constant operands if so ==> ADDI, ANDI, ORI 
             * EDIT: NOTE THAT according to Tiger-IR manual... FIRST OPERAND MUST BE A VARIABLE --> so if constant, MUST be operand 2 !!!!!!
            */

            // the following are pretty trivial cases
            case ADD:
                String add_dest_name = ((IRVariableOperand) instruc.operands[0]).getName();
                int add_dest_offset = offsets_stack.get(add_dest_name);
                IROperand add_src1 = instruc.operands[1];
                IROperand add_src2 = instruc.operands[2];

                load_operand_to_physical_reg(add_src1, t0, func); // $t0 = src1 AND LET'S DO THIS FOR ALL OF THESE BASIC ARITHMETIC OPERATIONS
                load_operand_to_physical_reg(add_src2, t1, func); // $t1 = src2
                if (add_src2 instanceof IRConstantOperand) {
                    Imm imm_operand = new Imm(((IRConstantOperand) add_src2).getValueString(), "DEC");
                    add_regular_to_mips(MIPSOp.ADDI, t0, t0, imm_operand);
                } else {
                    add_regular_to_mips(MIPSOp.ADD, t0, t0, t1);
                }
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + add_dest_offset, "DEC"), fp));
                break;
            case SUB:
                String sub_dest_name = ((IRVariableOperand) instruc.operands[0]).getName();
                int sub_dest_offset = offsets_stack.get(sub_dest_name);
                IROperand sub_src1 = instruc.operands[1];
                IROperand sub_src2 = instruc.operands[2];
                load_operand_to_physical_reg(sub_src1, t0, func);
                load_operand_to_physical_reg(sub_src2, t1, func);
                if (sub_src2 instanceof IRConstantOperand) { // if it is a constant then let's do ADDI else regular SUB
                    Imm imm_operand = new Imm(((IRConstantOperand) sub_src2).getValueString(), "DEC");
                    add_regular_to_mips(MIPSOp.ADDI, t0, t0, new Imm("-" + imm_operand.toString(), "DEC"));
                } else {
                    add_regular_to_mips(MIPSOp.SUB, t0, t0, t1);
                }
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + sub_dest_offset, "DEC"), fp));
                break;
            case MULT:
                String mult_dest_name = ((IRVariableOperand) instruc.operands[0]).getName();
                int mult_dest_offset = offsets_stack.get(mult_dest_name);
                IROperand mult_src1 = instruc.operands[1];
                IROperand mult_src2 = instruc.operands[2];

                load_operand_to_physical_reg(mult_src1, t0, func);
                load_operand_to_physical_reg(mult_src2, t1, func);

                add_regular_to_mips(MIPSOp.MUL, t0, t0, t1); 
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + mult_dest_offset, "DEC"), fp));
                break;
            case DIV:
                String div_dest_name = ((IRVariableOperand) instruc.operands[0]).getName();
                int div_dest_offset = offsets_stack.get(div_dest_name);
                IROperand div_src1 = instruc.operands[1];
                IROperand div_src2 = instruc.operands[2];

                load_operand_to_physical_reg(div_src1, t0, func);
                load_operand_to_physical_reg(div_src2, t1, func);

                add_regular_to_mips(MIPSOp.DIV, t0, t0, t1);
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + div_dest_offset, "DEC"), fp));
                break;
            case AND:
                String and_dest_name = ((IRVariableOperand) instruc.operands[0]).getName();
                int and_dest_offset = offsets_stack.get(and_dest_name);
                IROperand and_src1 = instruc.operands[1];
                IROperand and_src2 = instruc.operands[2];

                load_operand_to_physical_reg(and_src1, t0, func);
                load_operand_to_physical_reg(and_src2, t1, func);
                if (and_src2 instanceof IRConstantOperand) {
                    Imm imm_operand = new Imm(((IRConstantOperand) and_src2).getValueString(), "DEC");
                    add_regular_to_mips(MIPSOp.ANDI, t0, t0, imm_operand);
                } else {
                    add_regular_to_mips(MIPSOp.AND, t0, t0, t1);
                }
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + and_dest_offset, "DEC"), fp));
                break;
            case OR:
                String or_dest_name = ((IRVariableOperand) instruc.operands[0]).getName();
                int or_dest_offset = offsets_stack.get(or_dest_name);
                IROperand or_src1 = instruc.operands[1];
                IROperand or_src2 = instruc.operands[2];

                load_operand_to_physical_reg(or_src1, t0, func);
                load_operand_to_physical_reg(or_src2, t1, func);
                if (or_src2 instanceof IRConstantOperand) {
                    Imm imm_operand = new Imm(((IRConstantOperand) or_src2).getValueString(), "DEC");
                    add_regular_to_mips(MIPSOp.ORI, t0, t0, imm_operand);
                } else {
                    add_regular_to_mips(MIPSOp.OR, t0, t0, t1);
                }
                add_regular_to_mips(MIPSOp.SW, t0, new Addr(new Imm("" + or_dest_offset, "DEC"), fp));
                break;
            // NOTE: branch will also all look pretty similar with just the MIPS instruction operations pratically changing
            // instruction has "op", "lable", "operands"
            case BREQ:
                String breq_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String breq_unique_label = func.name + "_" + breq_label_name;
                Addr breq_label_addr = new Addr(breq_unique_label);
                IROperand breq_op1 = instruc.operands[1];
                IROperand breq_op2 = instruc.operands[2];

                load_operand_to_physical_reg(breq_op1, t0, func);
                load_operand_to_physical_reg(breq_op2, t1, func);

                add_regular_to_mips(MIPSOp.BEQ, breq_label_addr, t0, t1);
                break;
            case BRNEQ:
                String brneq_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String brneq_unique_label = func.name + "_" + brneq_label_name;
                Addr brneq_label_addr = new Addr(brneq_unique_label);
                IROperand brneq_op1 = instruc.operands[1];
                IROperand brneq_op2 = instruc.operands[2];

                load_operand_to_physical_reg(brneq_op1, t0, func);
                load_operand_to_physical_reg(brneq_op2, t1, func);

                add_regular_to_mips(MIPSOp.BNE, brneq_label_addr, t0, t1);
                break;
            case BRLT:
                String brlt_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String brlt_unique_label = func.name + "_" + brlt_label_name;
                Addr brlt_label_addr = new Addr(brlt_unique_label);
                IROperand brlt_op1 = instruc.operands[1];
                IROperand brlt_op2 = instruc.operands[2];

                load_operand_to_physical_reg(brlt_op1, t0, func);
                load_operand_to_physical_reg(brlt_op2, t1, func);

                add_regular_to_mips(MIPSOp.BLT, brlt_label_addr, t0, t1);
                break;
            case BRGT:
                String brgt_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String brgt_unique_label = func.name + "_" + brgt_label_name;
                Addr brgt_label_addr = new Addr(brgt_unique_label);
                IROperand brgt_op1 = instruc.operands[1];
                IROperand brgt_op2 = instruc.operands[2];

                load_operand_to_physical_reg(brgt_op1, t0, func);
                load_operand_to_physical_reg(brgt_op2, t1, func);

                add_regular_to_mips(MIPSOp.BGT, brgt_label_addr, t0, t1);
                break;
            case BRGEQ:
                // let's generate a unique label for each and load operands into registers
                String brgeq_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String brgeq_unique_label = func.name + "_" + brgeq_label_name;
                Addr brgeq_label_addr = new Addr(brgeq_unique_label);
                IROperand brgeq_op1 = instruc.operands[1];
                IROperand brgeq_op2 = instruc.operands[2];

                load_operand_to_physical_reg(brgeq_op1, t0, func);
                load_operand_to_physical_reg(brgeq_op2, t1, func);

                add_regular_to_mips(MIPSOp.BGE, brgeq_label_addr, t0, t1);
                break;
            
            // the next 4 are all control flow instructions
            case GOTO:
                String g_label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                String g_unique_label = func.name + "_" + g_label_name;
                add_regular_to_mips(MIPSOp.J, new Addr(g_unique_label));
                break;
            /*
            case RETURN:
                if (function.name.equals("main")) {
                    // this is the syscall used in main to "exit"
                    mips_program.instructions.put(curr_line_num, new MIPSInstruction(MIPSOp.LI, null, new Register("$v0"), new Imm("10", "DEC")));
                    curr_line_num++;
                    // make this syscall now !!!
                    mips_program.instructions.put(curr_line_num, new MIPSInstruction(MIPSOp.SYSCALL, null));
                    curr_line_num++;
                } else { // any other function
                    if (instruc.operands.length > 0) { // place return val in $v0
                        mips_program.instructions.put(curr_line_num, new MIPSInstruction(MIPSOp.MOVE, null, new Register("$v0"), new Register(instruc.operands[0].toString())));
                        curr_line_num++;
                    }
                    // restore registers (ra is stored at frame_size - 4), (fp is stored at frame_size - 8)
                    // move fp into sp
                    MIPSInstruction move_sp = new MIPSInstruction(MIPSOp.MOVE, null, new Register("$sp"), new Register("$fp"));
                    mips_program.instructions.put(curr_line_num, move_sp);
                    curr_line_num++;

                    // get the return addrress and place into $ra
                    Imm return_offset = new Imm("" + (frame_size - 4 ), "DEC");
                    MIPSInstruction load_ret_addr = new MIPSInstruction(MIPSOp.LW, null, new Register("$ra", false), new Addr(return_offset, new Register("$sp", false)));
                    mips_program.instructions.put(curr_line_num, load_ret_addr);
                    curr_line_num++;

                    //now we want to restore the frame pointer to the previous call frame
                    Imm frame_offset = new Imm("" + (frame_size - 8), "DEC");
                    MIPSInstruction restore_fp = new MIPSInstruction(MIPSOp.LW, null, new Register("$fp"), new Addr(frame_offset, new Register("$sp"))); // prev fp
                    mips_program.instructions.put(curr_line_num, restore_fp);
                    curr_line_num++;


                    // now we want to finally reset the stack pointer to the "caller's" stack pointer before returning control flow back to it
                    Imm restore_sp = new Imm("" + (frame_size), "DEC"); // our current frame is frame_size so if we add by this amount, we will get to the "top" (bottom, since grows downwards) of the stack
                    MIPSInstruction old_sp = new MIPSInstruction(MIPSOp.ADDI, null, new Register("$sp"), new Register("$sp"), restore_sp);
                    mips_program.instructions.put(curr_line_num, old_sp);
                    curr_line_num++;


                    // now that we have restored stack values into respective registers, we can jump back to the return address we have in $ra
                    MIPSInstruction jump_to = new MIPSInstruction(MIPSOp.JR, null, new Register("$ra"));
                    mips_program.instructions.put(curr_line_num, jump_to);
                    curr_line_num++;
                }
                break;
                */

            case RETURN:
                /* BRAINSTORMING IDEA first:
                if has a return:
                    - popping from stack (second part of calling convention)
                    - get the return address
                    - jump to the return address
                if the last instruction is not a return and we are not in main
                    --> go to the return address saved on the stack (from the caller)
                    
                if we are in main (at the end)
                    --> do a syscall to tell that exit code = 10 (???) */
                // original had check if it was main or not, but then realized that if it is, then we would not reach a RETURN instruction ==> moved handling to translate_function end
                if (curr_total_frame_size > 0) {
                    // if we have an $ra slot (value saved)
                    if (current_is_non_leaf) {
                        add_regular_to_mips(MIPSOp.LW, ra, new Addr(new Imm("4", "DEC"), fp));
                    }
                    // let's now restore the prev saved fp
                    add_regular_to_mips(MIPSOp.LW, fp, new Addr(new Imm("0", "DEC"), fp));
                    // let's set sp back to caller's stack frame (restore) ==> get rid of this entire current frame
                    add_regular_to_mips(MIPSOp.ADDI, sp, sp, new Imm("" + curr_total_frame_size, "DEC")); 
                }
                // now let's return execution back to the caller
                add_regular_to_mips(MIPSOp.JR, ra);
                break;

            case CALL:
            case CALLR:
                // NOTE FORMAT:
                /* CALL: op, func_name, param1, param2, ...
                   CALLR: op, x, func_name, param1, param2, ... */

                String func_name = "";
                IROperand dest_operand = null;
                IROperand func_operand = null;
                if (instruc.opCode == IRInstruction.OpCode.CALL) {
                    func_operand = instruc.operands[0];
                    func_name = ((IRFunctionOperand) func_operand).getName();
                } else {
                    dest_operand = instruc.operands[0];
                    func_operand = instruc.operands[1];
                    if (func_operand instanceof IRFunctionOperand) {
                        func_name = ((IRFunctionOperand) func_operand).getName();
                    } else { // Variable target
                        // Use variable name for intrinsic check, actual address loaded later
                        func_name = ((IRVariableOperand) func_operand).getName();
                    }
                }

                if (func_name.equals("geti") || func_name.equals("getc") || func_name.equals("puti") || func_name.equals("putc")) { // if this is an intrinsic call (predefined from Tiger-IR)
                    if (instruc.opCode == IRInstruction.OpCode.CALL) {
                        handle_intrinsic_function(instruc, func_name, 1, dest_operand, func);
                    } else {
                        handle_intrinsic_function(instruc, func_name, 2, dest_operand, func);
                    }
                } else { // we have a "regular" function call
                    int num_args;
                    // based on format, our relevant operands begin at different "indices" depending on CALL vs CALLR
                    if (instruc.opCode == IRInstruction.OpCode.CALL) {
                        num_args = instruc.operands.length - 1;
                    } else {
                        num_args = instruc.operands.length - 2;
                    }

                    // argument setup on the stack
                    for (int i = 0; i < num_args; i++) {
                        IROperand arg_operand;
                        // we offset the index because of the number of operands that are present between call and callr
                        if (instruc.opCode == IRInstruction.OpCode.CALL) {
                            arg_operand = instruc.operands[i + 1];
                        } else {
                            arg_operand = instruc.operands[i + 2];
                        }
                        load_operand_to_physical_reg(arg_operand, t0, func); // $t0 = arg value
                        if (i < 4) { // let's store it in one of the reserved argument registers OTHERWISE we are SPILLING onto the stack!!!!
                            Register dest_arg_reg = get_arg_register(i);
                            add_regular_to_mips(MIPSOp.MOVE, dest_arg_reg, t0);
                        } else { // here we have used all argument registers $a0-$a3 ==> SPILL onto stack!!!!!
                            // remember that our spilled arguments will be placed right "below" the $sp for the current stack frame
                                // ==> this means that the callee will have to access these locations at a positive offset from their $fp
                            int spill_sp_offset = i * WORD_SIZE;
                            Addr spill_addr = new Addr(new Imm("" + spill_sp_offset, "DEC"), sp);
                            add_regular_to_mips(MIPSOp.SW, t0, spill_addr);
                        }
                    }

                    // now we just jump to our label (function)
                    add_regular_to_mips(MIPSOp.JAL, new Addr(func_name));

                    // if we are CALLR then we want to retrieve the return value right after execution resumes in this function
                    if (instruc.opCode == IRInstruction.OpCode.CALLR && dest_operand instanceof IRVariableOperand) {
                        String dest_name = ((IRVariableOperand) dest_operand).getName();
                        int dest_offset = offsets_stack.get(dest_name);
                        Addr saved_val_location = new Addr(new Imm("" + dest_offset, "DEC"), fp);
                        add_regular_to_mips(MIPSOp.SW, v0, saved_val_location); // now let's store the $v0 result onto the stack !!!!
                    }
                }
                break;
            
            /* after the JAL then get this value placed into v0 into the register provided
                int arg_index;
                String label;
                if (instruc.opCode == OpCode.CALL) {
                    arg_index = 1;
                    label = ((IRLabelOperand) instruc.operands[0]).getName();
                } else {
                    arg_index = 2; // because extra operand (destination)
                    label = ((IRLabelOperand) instruc.operands[1]).getName();
                }
                // we loop n number of arguments times
                for (int i = arg_index; i < instruc.operands.length; i++) {
                    if (i < (arg_index+4)) { // while less than 4 args (store into $a(x) register)
                        MIPSInstruction store_arg = new MIPSInstruction(MIPSOp.MOVE, null, new Register("$a"+i, false), new Register(instruc.operands[i].toString()));
                        mips_program.instructions.put(curr_line_num, store_arg);
                        curr_line_num++;
                    } else {
                        Addr offset = new Addr(new Imm("" + ((i-6)*4)), "DEC");
                        MIPSInstruction store_on_stack = new MIPSInstruction(MIPSOp.SW, null, new Register(instruc.operands[i].toString()), offset, new Register("$sp"));
                        mips_program.instructions.put(curr_line_num, store_on_stack);
                        curr_line_num++;
                    }
                }
                MIPSInstruction jump_link = new MIPSInstruction(MIPSOp.JAL, null, new Addr(label));
                mips_program.instructions.put(curr_line_num, jump_link);
                curr_line_num++;

                // do i have to restore all $t registers?

                // CALLR --> get return value from $v0
                if (instruc.opCode == OpCode.CALLR) {
                    MIPSInstruction into_v0 = new MIPSInstruction(MIPSOp.MOVE, null, new Register(((IRLabelOperand) instruc.operands[0]).getName()), new Register("$v0", false));
                    mips_program.instructions.put(curr_line_num, into_v0);
                    curr_line_num++;
                }
                break;
                 OLD CODE SNIPPPETSSS */


            // these are all array operations
            // for array indexing, we may care about SLL because we want to access (index * size of types stored)
                // i.e. ==> arr[1] = arr[1*sizeof(int)] or something like that

            // this is ARRAY_STORE: op , x, array_name, offset
                // operand[0] = x (value), operand[1] = array_base_addr, operand[2] = offset
            case ARRAY_STORE:

                // this is to get the isntruction's information
                IROperand as_src = instruc.operands[0];
                IRVariableOperand as_array_var = (IRVariableOperand) instruc.operands[1];
                IROperand as_index = instruc.operands[2];
                String as_array_name = as_array_var.getName();

                // let's store the value we want to store into $t0
                load_operand_to_physical_reg(as_src, t0, func);
                // let's store the index in $t1
                load_operand_to_physical_reg(as_index, t1, func);

                Register as_final_addr;
                boolean as_is_param = false;
                for (IRVariableOperand param : func.parameters) {
                    if (param.getName().equals(as_array_name)) {
                        as_is_param = true;
                    }
                }
                if (as_is_param) {
                    // array_var is param ==> load base address (val)
                    int param_offset = offsets_stack.get(as_array_name);
                    Addr addr = new Addr(new Imm("" + param_offset, "DEC"), fp);
                    add_regular_to_mips(MIPSOp.LW, t2, addr); // let's store base address of the array in $t2
                    add_regular_to_mips(MIPSOp.SLL, t3, t1, new Imm("2", "DEC")); // $t3 = index * 4
                    add_regular_to_mips(MIPSOp.ADD, t2, t2, t3); // $t2 = $t2 + $t3
                    as_final_addr = t2;
                } else {
                    int array_offset = offsets_stack.get(as_array_name);
                    as_final_addr = calculate_array_address(array_offset, t1, t2, t3); // this follows the same format as the other calls
                }

                Addr as_store_to = new Addr(new Imm("0", "DEC"), as_final_addr);
                add_regular_to_mips(MIPSOp.SW, t0, as_store_to); // let's store value to specified location
                break;

            //this is ARRAY_LOAD: op, x, array_base, offset
                // operand[0] = x (value) ==> we are setting this variable to the following, operand[1] = array_base, operand[2] = offset (i.e. index)
            case ARRAY_LOAD:
                // this is to get the instructinos information
                IRVariableOperand al_dest = (IRVariableOperand) instruc.operands[0];
                IRVariableOperand al_array_var = (IRVariableOperand) instruc.operands[1];
                IROperand al_index = instruc.operands[2];
                String al_array_name = al_array_var.getName();

                String al_dest_name = al_dest.getName();
                int al_dest_offset = offsets_stack.get(al_dest_name);

                // let's store the index inside of $t0
                load_operand_to_physical_reg(al_index, t0, func);

                Register al_final_addr;
                boolean al_is_param = false;
                for (IRVariableOperand param : func.parameters) {
                    if (param.getName().equals(al_array_name)) {
                        al_is_param = true;
                    }
                }
                if (al_is_param) {
                    int param_offset = offsets_stack.get(al_array_name);
                    Addr addr = new Addr(new Imm("" + param_offset, "DEC"), fp);
                    add_regular_to_mips(MIPSOp.LW, t1, addr); // let's store base address of the array in $t1
                    add_regular_to_mips(MIPSOp.SLL, t2, t0, new Imm("2", "DEC")); // $t2 = index * 4
                    add_regular_to_mips(MIPSOp.ADD, t1, t1, t2); // we find the memory location of where we want to load to
                    al_final_addr = t1;
                } else {
                    int array_offset = offsets_stack.get(al_array_name);
                    al_final_addr = calculate_array_address(array_offset, t0, t1, t2);
                }
                Addr load_from = new Addr(new Imm("0", "DEC"), al_final_addr);
                add_regular_to_mips(MIPSOp.LW, t0, load_from); // let's load our desired val into $t0
                Addr al_store_to = new Addr(new Imm("" + al_dest_offset, "DEC"), fp);
                add_regular_to_mips(MIPSOp.SW, t0, al_store_to); // finally let's store it to specified location
                break;
        }
    }

    // this is used to generate unique label names
    private String get_unique_label(String prefix) {
        String full_str = prefix + "_" + label_counter;
        label_counter++;
        return full_str;
    }

    // this is used to determine which register to use when saving arguments from $a0-$a3
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

    // this is used to handle intrinsic function calls according to Tiger-IR manual
    // each of them has a separate "code"
    private void handle_intrinsic_function(IRInstruction instruc, String func_name, int idx_first_arg, IROperand dest_operand, IRFunction function) {
        switch (func_name) {
            case "geti": // for MIPS we load 5 into v0 to treat it as geti syscall
                add_regular_to_mips(MIPSOp.LI, v0, new Imm("5", "DEC"));
                add_regular_to_mips(MIPSOp.SYSCALL);
                if (dest_operand instanceof IRVariableOperand) {
                    String dest_name = ((IRVariableOperand) dest_operand).getName();
                    int dest_offset = offsets_stack.get(dest_name);
                    add_regular_to_mips(MIPSOp.SW, v0, new Addr(new Imm("" + dest_offset, "DEC"), fp));
                }
                break;
            case "getc": // for MIPS we load 12 into v0 to reart it as a getc syscall
                add_regular_to_mips(MIPSOp.LI, v0, new Imm("12", "DEC"));
                add_regular_to_mips(MIPSOp.SYSCALL);
                // Result is in v0 according to Figure A.9.1, not a0
                if (dest_operand instanceof IRVariableOperand) {
                    String dest_name = ((IRVariableOperand) dest_operand).getName();
                    int dest_offset = offsets_stack.get(dest_name);
                    add_regular_to_mips(MIPSOp.SW, v0, new Addr(new Imm("" + dest_offset, "DEC"), fp));
                }
                break;
            case "puti": // for MIPS we load 1 into v0 to treat it as a puti syscall
            if (instruc.operands.length > idx_first_arg) {
                load_operand_to_physical_reg(instruc.operands[idx_first_arg], a0, function);
                add_regular_to_mips(MIPSOp.LI, v0, new Imm("1", "DEC"));
                add_regular_to_mips(MIPSOp.SYSCALL);
            }
            break;
            case "putc": // for MIPS we load 11 into v0 to treat it as a putc syscall
                if (instruc.operands.length > idx_first_arg) {
                    load_operand_to_physical_reg(instruc.operands[idx_first_arg], a0, function);
                    add_regular_to_mips(MIPSOp.LI, v0, new Imm("11", "DEC"));
                    add_regular_to_mips(MIPSOp.SYSCALL);
                }
                break;
        }
    }

    /* here we generate MIPS isntructions and then load IR op's value/addr into specific target register */
    private void load_operand_to_physical_reg(IROperand op, Register target_reg, IRFunction func) {
        // first we want to check if it a constant ==> if so we get its immediate value
        if (op instanceof IRConstantOperand) {
            String value = ((IRConstantOperand) op).getValueString();
            add_regular_to_mips(MIPSOp.LI, target_reg, new Imm(value, "DEC"));
        } else if (op instanceof IRVariableOperand) { // if it is a variable then it could be array
            IRVariableOperand var = (IRVariableOperand) op;
            String var_name = var.getName();

            int offset = offsets_stack.get(var_name);

            if (var.type instanceof IRArrayType) {
                // let's see if it is a parameter
                boolean is_param = false;
                for (IRVariableOperand param : func.parameters) { // we want to handle if parameter or local variable differently!!!
                    if (param.getName().equals(var_name)) {
                        is_param = true;
                    }
                }

                if (is_param) {
                    // the array is a parameter... let's store its base pointer to the stack
                    Addr base_addr = new Addr(new Imm("" + offset, "DEC"), fp);
                    add_regular_to_mips(MIPSOp.LW, target_reg, base_addr);
                } else {
                    // it's a local array so let's just get its address
                    add_regular_to_mips(MIPSOp.ADDI, target_reg, fp, new Imm("" + offset, "DEC"));
                }
            } else {
                // constant ==> let's directly load its value
                Addr addr_of_val = new Addr(new Imm("" + offset, "DEC"), fp);
                add_regular_to_mips(MIPSOp.LW, target_reg, addr_of_val);
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

    // this is how to calculate the address of the array
    private Register calculate_array_address(int base_offset, Register index_reg, Register addr_reg, Register offset_index_reg) {
        add_regular_to_mips(MIPSOp.SLL, offset_index_reg, index_reg, new Imm("2", "DEC")); // ~ multiply by size of each element (4 bytes)
        add_regular_to_mips(MIPSOp.ADDI, addr_reg, fp, new Imm("" + base_offset, "DEC")); // find location with offset from the $fp of stack
        add_regular_to_mips(MIPSOp.ADD, addr_reg, addr_reg, offset_index_reg); // calc memory address for specific element
        return addr_reg;
    }

    // FIX MESSYNESS this is going to be the main add_regular_to_mips which all other functions call
    // bunch of overloaded functions tho because different instructions may give different fields
    // without was also having trouble with parameter passing and null values... this worked keep!!!
    public void add_regular_to_mips(MIPSOp op, String label, MIPSOperand... operands) {
        MIPSInstruction instruc = new MIPSInstruction(op, label, operands);
        mips_program.add_instruction(instruc);
    }
    public void add_regular_to_mips(MIPSOp op, MIPSOperand... operands) {
        add_regular_to_mips(op, null, operands);
    }
    // this is for J, JAL, and LABEL isntructions
    public void add_regular_to_mips(MIPSOp op, Addr label_addr) {
        if (op == MIPSOp.LABEL) {
                add_regular_to_mips(op, label_addr.toString(), new MIPSOperand[0]);
        } else { // J, JAL
            add_regular_to_mips(op, (MIPSOperand)label_addr);
        }
    }
    // used for the one LA instruction
    public void add_regular_to_mips(MIPSOp op, Register dest_reg, Addr label_addr) {
        add_regular_to_mips(op, (MIPSOperand)dest_reg, (MIPSOperand)label_addr);
    }
    // Branch overload (label, r1, r2)
    public void add_regular_to_mips(MIPSOp op, Addr label, Register src1, Register src2) {
        add_regular_to_mips(op, src1, src2, label);
    }

    // tihs is used for JR instruction
    public void add_regular_to_mips(MIPSOp op, Register target_reg) {
        add_regular_to_mips(op, (MIPSOperand) target_reg);
    }
}