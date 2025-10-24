.text
  j main
quicksort:
  addi $sp, $sp, -480
  sw $ra, 476($sp)
  sw $fp, 472($sp)
  addi $fp, $sp, 472
  sw $a0, -4($fp)
  sw $a1, -8($fp)
  sw $a2, -12($fp)
  lw $t0, -420($fp)
  lw $t1, -416($fp)
  bge $t0, $t1, quicksort_end
  lw $t0, -420($fp)
  lw $t1, -416($fp)
  add $t0, $t0, $t1
  sw $t0, -440($fp)
  lw $t0, -440($fp)
  li $t1, 2
  div $t0, $t0, $t1
  sw $t0, -440($fp)
  lw $t0, -440($fp)
  sll $t2, $t0, 2
  addi $t1, $fp, -412
  add $t1, $t1, $t2
  lw $t0, 0($t1)
  sw $t0, -444($fp)
  lw $t0, -420($fp)
  li $t1, 1
  addi $t0, $t0, -1
  sw $t0, -448($fp)
  lw $t0, -416($fp)
  li $t1, 1
  addi $t0, $t0, 1
  sw $t0, -452($fp)
quicksort_loop0:
quicksort_loop1:
  lw $t0, -448($fp)
  li $t1, 1
  addi $t0, $t0, 1
  sw $t0, -448($fp)
  lw $t0, -448($fp)
  sll $t2, $t0, 2
  addi $t1, $fp, -412
  add $t1, $t1, $t2
  lw $t0, 0($t1)
  sw $t0, -436($fp)
  lw $t0, -436($fp)
  sw $t0, -424($fp)
  lw $t0, -424($fp)
  lw $t1, -444($fp)
  blt $t0, $t1, quicksort_loop1
quicksort_loop2:
  lw $t0, -452($fp)
  li $t1, 1
  addi $t0, $t0, -1
  sw $t0, -452($fp)
  lw $t0, -452($fp)
  sll $t2, $t0, 2
  addi $t1, $fp, -412
  add $t1, $t1, $t2
  lw $t0, 0($t1)
  sw $t0, -436($fp)
  lw $t0, -436($fp)
  sw $t0, -428($fp)
  lw $t0, -428($fp)
  lw $t1, -444($fp)
  bgt $t0, $t1, quicksort_loop2
  lw $t0, -448($fp)
  lw $t1, -452($fp)
  bge $t0, $t1, quicksort_exit0
  lw $t0, -424($fp)
  lw $t1, -452($fp)
  sll $t3, $t1, 2
  addi $t2, $fp, -412
  add $t2, $t2, $t3
  sw $t0, 0($t2)
  lw $t0, -428($fp)
  lw $t1, -448($fp)
  sll $t3, $t1, 2
  addi $t2, $fp, -412
  add $t2, $t2, $t3
  sw $t0, 0($t2)
  j quicksort_loop0
quicksort_exit0:
  lw $t0, -412($fp)
  move $a0, $t0
  lw $t0, -420($fp)
  move $a1, $t0
  lw $t0, -452($fp)
  move $a2, $t0
  jal quicksort
  lw $t0, -452($fp)
  li $t1, 1
  addi $t0, $t0, 1
  sw $t0, -452($fp)
  lw $t0, -412($fp)
  move $a0, $t0
  lw $t0, -452($fp)
  move $a1, $t0
  lw $t0, -416($fp)
  move $a2, $t0
  jal quicksort
quicksort_end:
  lw $ra, 4($fp)
  lw $fp, 0($fp)
  addi $sp, $sp, 480
  jr $ra
main:
  addi $sp, $sp, -440
  sw $ra, 436($sp)
  sw $fp, 432($sp)
  addi $fp, $sp, 432
  li $v0, 5
  syscall
  sw $v0, -412($fp)
  lw $t0, -412($fp)
  li $t1, 100
  bgt $t0, $t1, main_return
  lw $t0, -412($fp)
  li $t1, 1
  addi $t0, $t0, -1
  sw $t0, -412($fp)
  li $t0, 0
  sw $t0, -408($fp)
main_loop0:
  lw $t0, -408($fp)
  lw $t1, -412($fp)
  bgt $t0, $t1, main_exit0
  li $v0, 5
  syscall
  sw $v0, -404($fp)
  lw $t0, -404($fp)
  lw $t1, -408($fp)
  sll $t3, $t1, 2
  addi $t2, $fp, -400
  add $t2, $t2, $t3
  sw $t0, 0($t2)
  lw $t0, -408($fp)
  li $t1, 1
  addi $t0, $t0, 1
  sw $t0, -408($fp)
  j main_loop0
main_exit0:
  lw $t0, -400($fp)
  move $a0, $t0
  li $t0, 0
  move $a1, $t0
  lw $t0, -412($fp)
  move $a2, $t0
  jal quicksort
  li $t0, 0
  sw $t0, -408($fp)
main_loop1:
  lw $t0, -408($fp)
  lw $t1, -412($fp)
  bgt $t0, $t1, main_exit1
  lw $t0, -408($fp)
  sll $t2, $t0, 2
  addi $t1, $fp, -400
  add $t1, $t1, $t2
  lw $t0, 0($t1)
  sw $t0, -404($fp)
  lw $a0, -404($fp)
  li $v0, 1
  syscall
  li $a0, 10
  li $v0, 11
  syscall
  lw $t0, -408($fp)
  li $t1, 1
  addi $t0, $t0, 1
  sw $t0, -408($fp)
  j main_loop1
main_exit1:
main_return:
  li $v0, 10
  syscall
