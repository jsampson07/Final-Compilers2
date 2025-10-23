import ir.*;
import ir.datatype.IRArrayType;
import ir.datatype.IRIntType;
import ir.datatype.IRType;
import ir.operand.*;
import main.java.mips.*;
import main.java.mips.operand.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstructionSelector {
    public MISPProgram mips_program;
    public Map<String, Integer> offsets_stack;
    public int frame_size;
    public int counter;
    // here are the actual registers for frame allocation
    public Register fp = new Register("$fp", false);
    public Register ra = new Register("$ra", false);
    public Register sp = new Register("$sp", false);
    public Register v0 = new Register("$v0", false);
    public Register a0 = new Register("$a0", false);
    public Register a1 = new Register("$a1", false);
    public Register a2 = new Register("$a2", false);
    public Register a3 = new Register("$a3", false);
    public Register at = new Register("$at", false);

    // this is where i translate the entire program
    public MIPSProgram select_instructions(IRProgram ir_program) {
        if (!ir_program) {
            return null;
        }
        mips_program = new MIPSProgram();
        mips_program.add_to_text_dirs(".text");
        mips_program.add_to_data_dirs(".global main");
        for (IRFunction function : ir_program.functions) {
            translate_function(function);
        }
    }

    public void translate_function(IRFunction function) {
        offsets_stack = new HashMap<>();
        /* WHAT DO WE WANT OUR STACK FRAME TO LOOK LIKE????
            - prev fp
            - ra
            - arguments from caller
            - saved */
        // now we will allocate space for ALL local variables
        //int curr_offset = -8; // we are offsetting from $fp and this points to base ==> $ra and old $fp are saved on the stack ALWAYS (or space ALWAYS allocated)
        int local_section_size = 0;
        for (IRVariableOperand var : function.variables) {
            if (var.type instanceof IRArrayType) { // if it is an array
                local_section_size += ((IRArrayType) var.type).getSize() * WORD_SIZE;
            } else { // otherwise it is 4 bytes (int/char)
                local_section_size += WORD_SIZE;
            }
            //curr_offset-=size;
            // this is all going to be relative to $fp
        }

        // here we will be allocating space for the argument slots where they will be used by the caller but USED by the callee
        int arg_section_size = 0; // needed for outgoing arguments
        for (IRInstruction instruc : function.instructions) {
            if (instruc.opCode == IRInstruction.OpCode.CALL) {
                arg_section_size = Math.max(arg_section_size, instruc.operands.length - 1); // has one less operand
            } else if (instruc.opCode == IRInstruction.OpCode.CALLR) {
                arg_section_size = Math.max(arg_section_size, instruc.operands.length - 2);
            }
        }
        if (arg_section_size < 4) { // account for word_size here because before we just had a count of args
            arg_section_size = 4*WORD_SIZE; // we always want at least 4 slots allocated ($a0-$a3)
        } else {
            max_num_args_called*=WORD_SIZE; // if we have 4 or more then we just allocate the amount we have
        }



        int total_frame_size = local_section_size + arg_section_size + (2*WORD_SIZE); // 2*WORD_SIZE is for $ra and $fp (we will always allocate space for $ra here for simplicity
        if (total_frame_size % 8 != 0) {
            total_frame_size+=WORD_SIZE;
        }

        // we are NOT working with saved registers

        /* THIS WILL BE NEEDED FOR LATER TRUST ME!!!

        int args_size = 0;
        int num_args = function.parameters.length;
        // let's allocate space to save the incoming argument registers ($a0-$a3)
        for (int i = 0; i < num_args && i < 4; i++) {
            if (i < 4) {
                args_size += WORD_SIZE;
                //curr_offset -= WORD_SIZE;
                offsets_stack.put(function.parameters[i].getName(), curr_offset);
            } else { // if we are at arg 5 or more (this would have been placed on the stack by the caller)
                // so because fp is pointing to bottom of ra, we just need to read one "frame" up the stack to access the top arguments
                int arg_offset = 4 + (i-4)*WORD_SIZE;
                offsets_stack.put(function.parameters[i].getName(), arg_offset);
            }
        }
        */

        //int total_size = local_size + args_size + (2*WORD_SIZE); // for ra and fp
        // always allocate space for 4 args but what if more than 4 args????
        /*
        int max_num_args_called = 0;
        for (IRInstruction instruc : function) {
            if (instruc.opCode == CALL) {
                max_num_args_called = Math.max(num_args, instruc.operands.length - 1);
            } else if (instruc.opCode == CALLR) {
                max_num_args_called = Math.max(num_args, instruc.operands.length - 2);
            }
        }
        */

        //curr_offset -= max_num_args_called*WORD_SIZE;
        //int total_size = curr_offset/-1; // get the positive value of this

        // how to account for the number of arguments?
        /* if we have <=4 arguments, then we can just store everything on the stack,
        if we have > 4 arguments, then we MUST spill the values onto the stack */

        /* how caller-callee interact:
            caller:
                - before a function (THIS IS CALLED), the caller places arguments
                    (4) into $a0-$a3
                    --> if more than 4 arguments then the caller saves these on the stack
            calee:
                - in this function (before we execute anything), the callee saves these arg regs vals
                    ont its stack */

        // now I want to create the stack frame and perform calling convention
        /* we want to do: 
            - set the stack pointer to move "frame size" amount
            - then place return address
            - then place frame pointer */
        // set new sp value (after allocating frame size amount)
        if (total_frame_size > 0) {
            // we want to move the stack frame pointer to the top of the newly created stack frame
            Imm frame_size = new Imm("-" + (total_size), "DEC");
            add_regular_to_mips(MIPSOp.SUB, sp, sp, frame_size);
            // store value in $ra into memory location at offset
            Imm ret_offset = new Imm("-" + (total_size-4), "DEC");
            Addr ret_location = new Addr(ret_offset, sp);
            add_regular_to_mips(MIPSOp.SW, ra, ret_location);
            // store value in $fp into memory location at offset
            Imm frame_offset = new Imm("-" + (total_size-8), "DEC");
            Addr frame_location = new Addr(frame_offset, sp);
            add_regular_to_mips(MIPSOp.SW, fp, frame_location);
            // now lets set our frame pointer value after saving the previous frame pointer value
            Imm offset = new Imm("" + (total_size), "DEC");
            add_regular_to_mips(MIPSOp.ADDI, null, fp, sp, total_frame_size);
        }

        // now we want to map all of the registers and variables
        // start with argument registers ($a0-$a3)
        int curr_offset = -8; // always start after $ra and $fp

        for (IRInstruction instruc : function.instructions) {
            translate_ir_to_mips(instruc, function);
        }

        // perform resetting the stack here !!!

        //...

        return mips_program;
    }

    public void translate_ir_to_mips(IRInstruction instruc, IRFunction func) {
        switch(instruc.opCode) {
            case LABEL:
                break;
            // there are two versions of assign: 2 ops AND 3 ops (array initialization)
            case ASSIGN:
                break;
            case ADD:
                Register dest_reg = get_virtual_register(instruc.operands[0]);
                Register source_1 = get_virtual_register(instruc.operands[1]);
                if (instruc.operands[2] instanceof IRConstantOperand) {
                    // then handle with Imm
                    Imm immediate = new Imm(((IRConstantOperand) ir.operands[2]).getName(), "DEC");
                    add_regular_to_mips(MIPSOp.ADDI, null, dest_reg, source_1, immediate);
                } else {
                    // create a source_2 reg
                    Register source_2 = get_virtual_register(instruc.operands[2]);
                    add_regular_to_mips(MIPSOp.ADD, null, dest_reg, source_1, source_2);
                }
                break;
            case SUB:
                Register dest_reg = get_virtual_register(instruc.operands[0]);
                Register source_1 = get_virtual_register(instruc.operands[1]);
                if (instruc.operands[2] instanceof IRConstantOperand) {
                    // then handle with Imm
                    Imm immediate = new Imm(((IRConstantOperand) ir.operands[2]).getName(), "DEC");
                    add_regular_to_mips(MIPSOp.SUB, null, dest_reg, source_1, immediate);
                } else {
                    // create a source_2 reg
                    Register source_2 = get_virtual_register(instruc.operands[2]);
                    add_regular_to_mips(MIPSOp.SUB, null, dest_reg, source_1, source_2);
                }
                break;
            case MULT:
                Register dest_reg = get_virtual_register(instruc.operands[0]);
                Register source_1 = get_virtual_register(instruc.operands[1]);
                if (instruc.operands[2] instanceof IRConstantOperand) {
                    // then handle with Imm
                    Imm immediate = new Imm(((IRConstantOperand) ir.operands[2]).getName(), "DEC");
                    add_regular_to_mips(MIPSOp.MUL, null, dest_reg, source_1, immediate);
                } else {
                    // create a source_2 reg
                    Register source_2 = get_virtual_register(instruc.operands[2]);
                    add_regular_to_mips(MIPSOp.MUL, null, dest_reg, source_1, source_2);
                }
                break;
            case DIV:
                Register dest_reg = get_virtual_register(instruc.operands[0]);
                Register source_1 = get_virtual_register(instruc.operands[1]);
                if (instruc.operands[2] instanceof IRConstantOperand) {
                    // then handle with Imm
                    Imm immediate = new Imm(((IRConstantOperand) ir.operands[2]).getName(), "DEC");
                    add_regular_to_mips(MIPSOp.DIV, null, dest_reg, source_1, immediate);
                } else {
                    // create a source_2 reg
                    Register source_2 = get_virtual_register(instruc.operands[2]);
                    add_regular_to_mips(MIPSOp.DIV, null, dest_reg, source_1, source_2);
                }
                break;
            case AND:
                Register dest_reg = get_virtual_register(instruc.operands[0]);
                Register source_1 = get_virtual_register(instruc.operands[1]);
                if (instruc.operands[2] instanceof IRConstantOperand) {
                    // then handle with Imm
                    Imm immediate = new Imm(((IRConstantOperand) ir.operands[2]).getName(), "DEC");
                    add_regular_to_mips(MIPSOp.ANDI, null, dest_reg, source_1, immediate);
                } else {
                    // create a source_2 reg
                    Register source_2 = get_virtual_register(instruc.operands[2]);
                    add_regular_to_mips(MIPSOp.AND, null, dest_reg, source_1, source_2);
                }
                break;
            case OR:
                Register dest_reg = get_virtual_register(instruc.operands[0]);
                Register source_1 = get_virtual_register(instruc.operands[1]);
                if (instruc.operands[2] instanceof IRConstantOperand) {
                    // then handle with Imm
                    Imm immediate = new Imm(((IRConstantOperand) ir.operands[2]).getName(), "DEC");
                    add_regular_to_mips(MIPSOp.ORI, null, dest_reg, source_1, immediate);
                } else {
                    // create a source_2 reg
                    Register source_2 = get_virtual_register(instruc.operands[2]);
                    add_regular_to_mips(MIPSOp.OR, null, dest_reg, source_1, source_2);
                }
                break;
            case GOTO:
                String label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                add_regular_to_mips(MIPSOp.J, null, new Addr(label_name));
                break;
            /* not that for all these branch instructions...
                ==> we use gvr_for_branch because we may have immediates or variables */
            case BREQ:
                String label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                Addr label = new Addr(label_name);
                Register reg_1 = gvr_for_branch(instruc.operands[1]);
                Register reg_2 = gvr_for_branch(instruc.operands[2]);
                add_regular_to_mips(MIPSOp.BEQ, label, reg_1, reg_2);
                break;
            case BRNEQ:
                String label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                Addr label = new Addr(label_name);
                Register reg_1 = gvr_for_branch(instruc.operands[1]);
                Register reg_2 = gvr_for_branch(instruc.operands[2]);
                add_regular_to_mips(MIPSOp.BNE, label, reg_1, reg_2);
                break;
            case BRLT:
                String label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                Addr label = new Addr(label_name);
                Register reg_1 = gvr_for_branch(instruc.operands[1]);
                Register reg_2 = gvr_for_branch(instruc.operands[2]);
                add_regular_to_mips(MIPSOp.BLT, label, reg_1, reg_2);
                break;
            case BRGT:
                String label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                Addr label = new Addr(label_name);
                Register reg_1 = gvr_for_branch(instruc.operands[1]);
                Register reg_2 = gvr_for_branch(instruc.operands[2]);
                add_regular_to_mips(MIPSOp.BGT, label, reg_1, reg_2);
                break;
            case BRGEQ:
                String label_name = ((IRLabelOperand) instruc.operands[0]).getName();
                Addr label = new Addr(label_name);
                Register reg_1 = gvr_for_branch(instruc.operands[1]);
                Register reg_2 = gvr_for_branch(instruc.operands[2]);
                add_regular_to_mips(MIPSOp.BGE, label, reg_1, reg_2);
                break;
            case RETURN:
                // there is a special case if we are in main()

                break;
            case CALL:
            case CALLR:
                /* when a call is made what do we want?
                    we want calling convention !!!
                        -  */
                break;
            case ARRAY_STORE:
                break;
            case ARRAY_LOAD:
                break;
        }
    }

    public Register get_virtual_register(IROperand op) {
        if (!(op instanceof IRVariableOperand)) {
            return null;
        }
        return new Register(((IRVariableOperand) op).getName(), true);
    }

    public Register gvr_for_branch(IROperand op) {
        // if we have variable then just create a regular register
        if (op instanceof IRVariableOperand) {
            return get_virtual_register(op);
        }
        // if constant, must create a register to hold its value
        if (op instanceof IRConstantOperand) {
            Register reg = new Register("v_" + (counter), true);
            counter++;
            String val = ((IRConstantOperand) operand).getValueString();
            add_regular_to_mips(MIPSOp.LI, reg, new Imm(val, "DEC"));
            return reg;
        }
        return null;
    }

    public void add_regular_to_mips(MIPSOp op, String label, MIPSOperand... operands) {
        mips_program.add_instruction(op, label, operands);
    }
}