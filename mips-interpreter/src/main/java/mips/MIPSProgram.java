package main.java.mips;

import java.util.List;
import java.util.Map;

public class MIPSProgram {

   public Map<Integer, MIPSInstruction> instructions;
   public Map<Integer, Integer> data;
   public Map<String, Integer> labels;

   public List<MIPSInstruction> instruction_list;
   public List<String> data_dirs;
   public List<String> text_dirs;

    // need because inside select_instructions ==> we need to create a completely new (empty) MIPS program
        // --> if we use the other constructor then setting everything to null will not allow us to initialize as intended
   public MIPSProgram() {
        this.instructions = new HashMap<>();
        this.data = new HashMap<>();
        this.labels = new HashMap<>();
        this.instruction_list = new ArrayList<>();
        this.dataDirectives = new ArrayList<>();
        this.textDirectives = new ArrayList<>();
   }

   public MIPSProgram(Map<Integer, MIPSInstruction> instructions,
                      Map<Integer, Integer> data, Map<String, Integer> labels) {
       this.instructions = instructions;
       this.data = data;
       this.labels = labels;
       this.instruction_list = new ArrayList<>();
       this.data_dirs = new ArrayList<>();
       this.text_dirs = new ArrayList<>();
   }

   public void add_instruction(MIPSInstruction instruc) {
    this.instruction_list.add(instruc);
   }

   public void add_to_data_dirs(String dir) {
    this.data_dirs.add(dir);
   }

   public void add_to_text_dirs(String dir) {
    this.text_dirs.add(dir);
   }

   public void printLabels() {
       for (String label : labels.keySet()) {
           System.out.println(label + " -> " + Integer.toHexString(labels.get(label)));
       }
   }
}
