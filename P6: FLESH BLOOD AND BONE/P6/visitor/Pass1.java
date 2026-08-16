package visitor;

import syntaxtree.*;

public class Pass1 extends GJDepthFirst<Void, Void> {

    // to use in binop visit
    public String binop_dest_reg = null;
    // Per-procedure
    private int curArgCount = 0; // number of params this proc takes
    private int curInStackArgs = 0; // max(0, curArgCount - 4)
    private int curSlotsCount = 0; // number of SPILLEDARG slots used for locals/spills
    private int curMaxArgsCount = 0; // number of args this proc may pass to any callee
    private int curOutStackArgs = 0; // max(0, curMaxArgsCount - 4)
    private int curFrameSizeBytes = 0; // (curSlotsCount + curOutStackArgs)*4 + 8 (saved fp/ra)

    /**
     * f0 -> "MAIN"
     * f1 -> "["
     * f2 -> IntegerLiteral()
     * f3 -> "]"
     * f4 -> "["
     * f5 -> IntegerLiteral()
     * f6 -> "]"
     * f7 -> "["
     * f8 -> IntegerLiteral()
     * f9 -> "]"
     * f10 -> StmtList()
     * f11 -> "END"
     * f12 -> ( SpillInfo() )?
     * f13 -> ( Procedure() )*
     * f14 -> <EOF>
     */
    @Override
    public Void visit(Goal n, Void argu) {
        System.out.println(".text");
        System.out.println(".globl main");
        System.out.println("main:");
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);
        n.f9.accept(this, argu);
        // Frame parameters (counts, not bytes)
        curArgCount = Integer.parseInt(n.f2.f0.toString());
        curSlotsCount = Integer.parseInt(n.f5.f0.toString());
        curMaxArgsCount = Integer.parseInt(n.f8.f0.toString());
        curInStackArgs = Math.max(0, curArgCount - 4);
        curOutStackArgs = Math.max(0, curMaxArgsCount - 4);
        curFrameSizeBytes = (curSlotsCount + curOutStackArgs) * 4 + 8; // save fp/ra at tail
        // Frame layout (low addresses at $sp):
        // [0 .. slots-1]*4 : spilled slots / locals
        // [slots .. slots+outArgs-1] : outgoing arg area
        // [slots+outArgs]*4 : saved $fp
        // [slots+outArgs]*4 + 4 : saved $ra
        // prologue: allocate full frame then save $fp/$ra 
        System.out.println("move $fp, $sp");
        System.out.println("subu $sp, $sp, " + curFrameSizeBytes);
        System.out.println("sw $fp, " + ((curSlotsCount + curOutStackArgs) * 4) + "($sp)");
        System.out.println("sw $ra, " + ((curSlotsCount + curOutStackArgs) * 4 + 4) + "($sp)");
        n.f10.accept(this, argu);
        n.f11.accept(this, argu);
        n.f12.accept(this, argu);

        // epilogue: restore $ra/$fp from tail, then deallocate frame
        System.out.println("lw $ra, " + ((curSlotsCount + curOutStackArgs) * 4 + 4) + "($sp)");
        System.out.println("lw $fp, " + ((curSlotsCount + curOutStackArgs) * 4) + "($sp)");
        System.out.println("addu $sp, $sp, " + curFrameSizeBytes);

        // exit
        System.out.println("li $v0, 10");
        System.out.println("syscall");

        n.f13.accept(this, argu);
        n.f14.accept(this, argu);

        //helpers
        System.out.println(".text");
        System.out.println(".globl _halloc");
        System.out.println("_halloc:");
        System.out.println("li $v0, 9");
        System.out.println("syscall");
        System.out.println("jr $ra");

        System.out.println(".text");
        System.out.println(".globl _print");
        System.out.println("_print:");
        System.out.println("li $v0, 1");
        System.out.println("syscall");
        System.out.println("la $a0, newl");
        System.out.println("li $v0, 4");
        System.out.println("syscall");
        System.out.println("jr $ra");

        System.out.println(".data");
        System.out.println(".align 0");
        System.out.println("newl: .asciiz \"\\n\"");

        System.out.println(".data");
        System.out.println(".align 0");
        System.out.println("str_err: .asciiz \"Error: abnormal termination\\n\"");
        return _ret;
    }

    /**
     * f0 -> ( ( Label() )? Stmt() )*
     */
    public Void visit(StmtList n, Void argu) {
        Void _ret = null;
        if (n.f0 != null && n.f0.present()) {
            for (java.util.Enumeration<syntaxtree.Node> en = n.f0.elements(); en.hasMoreElements();) {
                syntaxtree.Node node = en.nextElement();
                syntaxtree.NodeSequence seq = (syntaxtree.NodeSequence) node;
                int idx = 0;
                syntaxtree.Label defLabel = null;

                // First slot might be Label, NodeOptional(Label), or directly Stmt
                syntaxtree.Node first = seq.elementAt(idx);
                if (first instanceof syntaxtree.Label) {
                    defLabel = (syntaxtree.Label) first;
                    idx++;
                } else if (first instanceof syntaxtree.NodeOptional) {
                    syntaxtree.NodeOptional opt = (syntaxtree.NodeOptional) first;
                    if (opt.present() && opt.node instanceof syntaxtree.Label) {
                        defLabel = (syntaxtree.Label) opt.node;
                    }
                    idx++;
                }

                if (defLabel != null) {
                    // only once
                    System.out.println(defLabel.f0.toString() + ":");
                }

                // Next element must be the Stmt
                syntaxtree.Stmt stmt = (syntaxtree.Stmt) seq.elementAt(idx);
                stmt.accept(this, argu);
            }
        }
        return _ret;
    }

    /**
     * f0 -> Label()
     * f1 -> "["
     * f2 -> IntegerLiteral()
     * f3 -> "]"
     * f4 -> "["
     * f5 -> IntegerLiteral()
     * f6 -> "]"
     * f7 -> "["
     * f8 -> IntegerLiteral()
     * f9 -> "]"
     * f10 -> StmtList()
     * f11 -> "END"
     * f12 -> ( SpillInfo() )?
     */
    @Override
    public Void visit(Procedure n, Void argu) {
        System.out.println(".text");
        System.out.println(".globl " + n.f0.f0.toString());
        System.out.println(n.f0.f0.toString() + ":");
        Void _ret = null;
        // n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);
        n.f9.accept(this, argu);
        // frame parameters (counts)
        curArgCount = Integer.parseInt(n.f2.f0.toString());
        curSlotsCount = Integer.parseInt(n.f5.f0.toString());
        curMaxArgsCount = Integer.parseInt(n.f8.f0.toString());
        curInStackArgs = Math.max(0, curArgCount - 4);
        curOutStackArgs = Math.max(0, curMaxArgsCount - 4);
        curFrameSizeBytes = (curSlotsCount + curOutStackArgs) * 4 + 8; // bytes
        // prologue
        System.out.println("move $fp, $sp");
        System.out.println("subu $sp, $sp, " + curFrameSizeBytes);
        System.out.println("sw $fp, " + ((curSlotsCount + curOutStackArgs) * 4) + "($sp)");
        System.out.println("sw $ra, " + ((curSlotsCount + curOutStackArgs) * 4 + 4) + "($sp)");
        n.f10.accept(this, argu);
        n.f11.accept(this, argu);
        n.f12.accept(this, argu);

        // epilogue
        System.out.println("lw $ra, " + ((curSlotsCount + curOutStackArgs) * 4 + 4) + "($sp)");
        System.out.println("lw $fp, " + ((curSlotsCount + curOutStackArgs) * 4) + "($sp)");
        System.out.println("addu $sp, $sp, " + curFrameSizeBytes);
        System.out.println("jr $ra");

        return _ret;
    }

    /**
     * f0 -> NoOpStmt()
     * | ErrorStmt()
     * | CJumpStmt()
     * | JumpStmt()
     * | HStoreStmt()
     * | HLoadStmt()
     * | MoveStmt()
     * | PrintStmt()
     * | ALoadStmt()
     * | AStoreStmt()
     * | PassArgStmt()
     * | CallStmt()
     */
    public Void visit(Stmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "NOOP"
     */
    @Override
    public Void visit(NoOpStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        System.out.println("nop");
        return _ret;
    }

    /**
     * f0 -> "ERROR"
     */
    public Void visit(ErrorStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "CJUMP"
     * f1 -> Reg()
     * f2 -> Label()
     */
    @Override
    public Void visit(CJumpStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        // n.f2.accept(this, argu);
        System.out.println("beqz $" + ((NodeToken) n.f1.f0.choice).toString() + ", " + n.f2.f0.toString());
        return _ret;
    }

    /**
     * f0 -> "JUMP"
     * f1 -> Label()
     */
    @Override
    public Void visit(JumpStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        // n.f1.accept(this, argu);
        System.out.println("b " + n.f1.f0.toString());
        return _ret;
    }

    /**
     * f0 -> "HSTORE"
     * f1 -> Reg()
     * f2 -> IntegerLiteral()
     * f3 -> Reg()
     */
    @Override
    public Void visit(HStoreStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        System.out.println("sw $" + n.f3.f0.choice.toString() + ", " + n.f2.f0.toString() + "($"
                + n.f1.f0.choice.toString() + ")");
        return _ret;
    }

    /**
     * f0 -> "HLOAD"
     * f1 -> Reg()
     * f2 -> Reg()
     * f3 -> IntegerLiteral()
     */
    @Override
    public Void visit(HLoadStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        System.out.println("lw $" + n.f1.f0.choice.toString() + ", " + n.f3.f0.toString() + "($"
                + n.f2.f0.choice.toString() + ")");
        return _ret;
    }

    /**
     * f0 -> "MOVE"
     * f1 -> Reg()
     * f2 -> Exp()
     */
    @Override // check
    public Void visit(MoveStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        // n.f2.accept(this, argu);
        // if f2 is binop we have to check operator type and use add, sub,...
        if (n.f2.f0.choice instanceof BinOp) {
            BinOp binop = (BinOp) n.f2.f0.choice;
            binop_dest_reg = ((NodeToken) n.f1.f0.choice).toString();
            ((BinOp) n.f2.f0.choice).accept(this, argu);// logic will print in binop visit
        }

        // if f2 is simpleexp check if reg or integerliteral
        else if (n.f2.f0.choice instanceof SimpleExp) {
            SimpleExp simpleexp = (SimpleExp) n.f2.f0.choice;
            if (simpleexp.f0.choice instanceof Reg) {
                Reg src = (Reg) simpleexp.f0.choice;
                System.out.println("move $" + ((NodeToken) n.f1.f0.choice).toString() + ", $"
                        + ((NodeToken) src.f0.choice).toString());
            } else if (simpleexp.f0.choice instanceof IntegerLiteral) {
                IntegerLiteral val = (IntegerLiteral) simpleexp.f0.choice;
                System.out.println("li $" + ((NodeToken) n.f1.f0.choice).toString() + ", " + val.f0.toString());
            } else {
                System.out.println("la $" + ((NodeToken) n.f1.f0.choice).toString() + ", "
                        + ((Label) simpleexp.f0.choice).f0.toString());
            }
        }
        // if f2 is hallocate,use jal _hallocate
        else if (n.f2.f0.choice instanceof HAllocate) {
            HAllocate halloc = (HAllocate) n.f2.f0.choice;
            halloc.accept(this, argu);
            // after jal _hallocate, the address is in $v0
            String hallocReg = null;
            if (halloc.f1.f0.choice instanceof Reg) {
                hallocReg = ((NodeToken) ((Reg) halloc.f1.f0.choice).f0.choice).toString();
                System.out.println("move $a0, $" + hallocReg);
            } else if (halloc.f1.f0.choice instanceof IntegerLiteral) {
                // if int,use li
                IntegerLiteral val = (IntegerLiteral) halloc.f1.f0.choice;
                System.out.println("li $a0, " + val.f0.toString());
            }
            System.out.println("jal _halloc");
            System.out.println("move $" + ((NodeToken) n.f1.f0.choice).toString() + ", $v0");
        }
        return _ret;
    }

    /**
     * f0 -> "PRINT"
     * f1 -> SimpleExp()
     */
    @Override
    public Void visit(PrintStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        if (n.f1.f0.choice instanceof Reg) {
            Reg reg = (Reg) n.f1.f0.choice;
            System.out.println("move $a0, $" + ((NodeToken) reg.f0.choice).toString());
        } else if (n.f1.f0.choice instanceof IntegerLiteral) {
            IntegerLiteral val = (IntegerLiteral) n.f1.f0.choice;
            System.out.println("li $a0, " + val.f0.toString());
        }
        System.out.println("jal _print");
        return _ret;
    }

    /**
     * f0 -> "ALOAD"
     * f1 -> Reg()
     * f2 -> SpilledArg()
     */
    @Override
    public Void visit(ALoadStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        int idx = Integer.parseInt(n.f2.f1.f0.toString());
        if (idx < curInStackArgs) {
            // incoming stack argument
            //  located at caller's $sp, which is accessible via $fp
            int offset = idx * 4; // first incoming arg at 0($fp)
            System.out.println("lw $" + ((NodeToken) n.f1.f0.choice).toString() + ", " + offset + "($fp)");
        } else {
            // spilled/local slot
            // locals live after the outgoing-arg area in the frame
            int localIndex = idx - curInStackArgs; // index among local spilled slots
            int offset = (curOutStackArgs * 4) + (localIndex * 4);
            System.out.println("lw $" + ((NodeToken) n.f1.f0.choice).toString() + ", " + offset + "($sp)");
        }
        return _ret;
    }

    /**
     * f0 -> "ASTORE"
     * f1 -> SpilledArg()
     * f2 -> Reg()
     */
    @Override
    public Void visit(AStoreStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        int idx = Integer.parseInt(n.f1.f1.f0.toString());
        if (idx < curInStackArgs) {
            // incoming stack slot 
            int offset = idx * 4;
            System.out.println("sw $" + ((NodeToken) n.f2.f0.choice).toString() + ", " + offset + "($fp)");
        } else {
            // spilled/local slot
            int localIndex = idx - curInStackArgs;
            int offset = (curOutStackArgs * 4) + (localIndex * 4);
            System.out.println("sw $" + ((NodeToken) n.f2.f0.choice).toString() + ", " + offset + "($sp)");
        }
        return _ret;
    }

    /**
     * f0 -> "PASSARG"
     * f1 -> IntegerLiteral()
     * f2 -> Reg()
     */
    public Void visit(PassArgStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        int k = Integer.parseInt(n.f1.f0.toString());
        int offset = (k - 1) * 4; // 0-based offset
        //  register name for PASSARG
        String passReg = null;
        if (n.f2.f0.choice instanceof Reg) {
            passReg = ((NodeToken) ((Reg) n.f2.f0.choice).f0.choice).toString();
        } else {
            passReg = n.f2.f0.choice.toString();
        }
        System.out.println("sw $" + passReg + ", " + offset + "($sp)");
        return _ret;
    }

    /**
     * f0 -> "CALL"
     * f1 -> SimpleExp()
     */
    @Override
    public Void visit(CallStmt n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        // CALL target can be a register (indirect) or a label (direct)
        if (n.f1.f0.choice instanceof Reg) {
            String callReg = ((NodeToken) ((Reg) n.f1.f0.choice).f0.choice).toString();
            System.out.println("jalr $" + callReg);
        } else if (n.f1.f0.choice instanceof Label) {
            String label = ((Label) n.f1.f0.choice).f0.toString();
            System.out.println("jal " + label);
        } else if (n.f1.f0.choice instanceof IntegerLiteral) {
            // Not typical
            String imm = ((IntegerLiteral) n.f1.f0.choice).f0.toString();
            System.out.println("li $t9, " + imm);
            System.out.println("jalr $t9");
        }
        return _ret;
    }

    /**
     * f0 -> HAllocate()
     * | BinOp()
     * | SimpleExp()
     */
    public Void visit(Exp n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "HALLOCATE"
     * f1 -> SimpleExp()
     */
    public Void visit(HAllocate n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> Operator()
     * f1 -> Reg()
     * f2 -> SimpleExp()
     */
    @Override
    public Void visit(BinOp n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        String op = ((NodeToken) n.f0.f0.choice).toString();
        // if f2 is reg,use add sub
        if (n.f2.f0.choice instanceof Reg) {
            Reg src = (Reg) n.f2.f0.choice;
            if (op.equals("PLUS")) {
                System.out.println("add $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", $"
                        + ((NodeToken) src.f0.choice).toString());
            } else if (op.equals("MINUS")) {
                System.out.println("sub $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", $"
                        + ((NodeToken) src.f0.choice).toString());
            } else if (op.equals("TIMES")) {
                System.out.println("mul $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", $"
                        + ((NodeToken) src.f0.choice).toString());
            } else if (op.equals("DIV")) {
                System.out.println("div $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", $"
                        + ((NodeToken) src.f0.choice).toString());
            } else if (op.equals("LE")) {
                System.out
                        .println("sle $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", $"
                                + ((NodeToken) src.f0.choice).toString());
            } else if (op.equals("NE")) {
                System.out
                        .println("sne $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", $"
                                + ((NodeToken) src.f0.choice).toString());
            }
        }
        // if f2 is integerliteral,load into a reg
        else if (n.f2.f0.choice instanceof IntegerLiteral) {
            IntegerLiteral val = (IntegerLiteral) n.f2.f0.choice;

            //load imm into a reg

            System.out.println("li $v1, " + val.f0.toString());

            if (op.equals("PLUS")) {
                System.out.println("add $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", "
                        +"$v1");
            } else if (op.equals("MINUS")) {
                System.out.println("sub $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", "
                        + "$v1");
            } else if (op.equals("TIMES")) {
                System.out.println("mul $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", "
                        +"$v1");
            } else if (op.equals("DIV")) {
                System.out.println("div $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", "
                        + "$v1");
            } else if (op.equals("LE")) {
                System.out
                        .println("sle $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", "
                                + "$v1");
            } else if (op.equals("NE")) {
                System.out
                        .println("sne $" + binop_dest_reg + ", $" + ((NodeToken) n.f1.f0.choice).toString() + ", "
                                + "$v1");
            }
        }
        return _ret;
    }

    /**
     * f0 -> "LE"
     * | "NE"
     * | "PLUS"
     * | "MINUS"
     * | "TIMES"
     * | "DIV"
     */
    public Void visit(Operator n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "SPILLEDARG"
     * f1 -> IntegerLiteral()
     */
    public Void visit(SpilledArg n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> Reg()
     * | IntegerLiteral()
     * | Label()
     */
    public Void visit(SimpleExp n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "a0"
     * | "a1"
     * | "a2"
     * | "a3"
     * | "t0"
     * | "t1"
     * | "t2"
     * | "t3"
     * | "t4"
     * | "t5"
     * | "t6"
     * | "t7"
     * | "s0"
     * | "s1"
     * | "s2"
     * | "s3"
     * | "s4"
     * | "s5"
     * | "s6"
     * | "s7"
     * | "t8"
     * | "t9"
     * | "v0"
     * | "v1"
     */
    public Void visit(Reg n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public Void visit(IntegerLiteral n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public Void visit(Label n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "//"
     * f1 -> SpillStatus()
     */
    public Void visit(SpillInfo n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> <SPILLED>
     * | <NOTSPILLED>
     */
    public Void visit(SpillStatus n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }
}
