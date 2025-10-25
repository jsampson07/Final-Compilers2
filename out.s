.text
   j main
divisible:
   addi $sp, $sp, -32
   sw $fp, 28($sp)
   addi $fp, $sp, 28
   sw $a0, -4($fp)
   sw $a1, -8($fp)
   lw $t0, -4($fp)
   lw $t1, -8($fp)
   div $t0, $t0, $t1
   sw $t0, -12($fp)
   lw $t0, -12($fp)
   lw $t1, -8($fp)
   mul $t0, $t0, $t1
   sw $t0, -12($fp)
   lw $t0, -4($fp)
   lw $t1, -12($fp)
   bne $t0, $t1, divisible_label0
   lw $fp, 0($fp)
   addi $sp, $sp, 32
   jr $ra
divisible_label0:
   lw $fp, 0($fp)
   addi $sp, $sp, 32
   jr $ra
   lw $fp, 0($fp)
   addi $sp, $sp, 32
   jr $ra
main:
   addi $sp, $sp, -80
   sw $ra, 76($sp)
   sw $fp, 72($sp)
   addi $fp, $sp, 72
   li $t0, 2
   sw $t0, -8($fp)
   li $t0, 3
   sw $t0, -12($fp)
   li $t0, 0
   sw $t0, -52($fp)
   li $v0, 5
   syscall
   sw $v0, -24($fp)
   lw $t0, -24($fp)
   li $t1, 1
   bgt $t0, $t1, main_label0
   li $t0, 0
   sw $t0, -28($fp)
   lw $t0, -28($fp)
   sw $t0, -4($fp)
   j main_print
main_label0:
   lw $t0, -24($fp)
   li $t1, 3
   bgt $t0, $t1, main_label1
   li $t0, 1
   sw $t0, -28($fp)
   lw $t0, -28($fp)
   sw $t0, -4($fp)
   j main_print
main_label1:
   lw $t0, -24($fp)
   move $a0, $t0
   lw $t0, -8($fp)
   move $a1, $t0
   jal divisible
   sw $v0, -36($fp)
   lw $t0, -52($fp)
   sw $t0, -28($fp)
   lw $t0, -28($fp)
   sw $t0, -4($fp)
   lw $t0, -36($fp)
   li $t1, 1
   beq $t0, $t1, main_label2
   lw $t0, -24($fp)
   move $a0, $t0
   lw $t0, -12($fp)
   move $a1, $t0
   jal divisible
   sw $v0, -36($fp)
   lw $t0, -52($fp)
   sw $t0, -28($fp)
   lw $t0, -28($fp)
   sw $t0, -4($fp)
   lw $t0, -36($fp)
   li $t1, 1
   beq $t0, $t1, main_label2
   j main_label3
main_label2:
   j main_print
main_label3:
   li $t0, 5
   sw $t0, -20($fp)
main_loop:
   lw $t0, -20($fp)
   lw $t1, -20($fp)
   mul $t0, $t0, $t1
   sw $t0, -32($fp)
   lw $t0, -32($fp)
   lw $t1, -24($fp)
   bgt $t0, $t1, main_exit
   lw $t0, -24($fp)
   move $a0, $t0
   lw $t0, -20($fp)
   move $a1, $t0
   jal divisible
   sw $v0, -36($fp)
   lw $t0, -52($fp)
   sw $t0, -28($fp)
   lw $t0, -28($fp)
   sw $t0, -4($fp)
   lw $t0, -36($fp)
   li $t1, 1
   beq $t0, $t1, main_label2
   lw $t0, -20($fp)
   li $t1, 2
   addi $t0, $t0, 2
   sw $t0, -40($fp)
   lw $t0, -24($fp)
   move $a0, $t0
   lw $t0, -40($fp)
   move $a1, $t0
   jal divisible
   sw $v0, -36($fp)
   lw $t0, -52($fp)
   sw $t0, -28($fp)
   lw $t0, -28($fp)
   sw $t0, -4($fp)
   lw $t0, -36($fp)
   li $t1, 1
   beq $t0, $t1, main_label2
   lw $t0, -20($fp)
   li $t1, 6
   addi $t0, $t0, 6
   sw $t0, -20($fp)
   j main_loop
main_exit:
   li $t0, 1
   sw $t0, -28($fp)
   lw $t0, -28($fp)
   sw $t0, -4($fp)
main_print:
   lw $a0, -4($fp)
   li $v0, 1
   syscall
   li $a0, 10
   li $v0, 11
   syscall
   li $v0, 10
   syscall
