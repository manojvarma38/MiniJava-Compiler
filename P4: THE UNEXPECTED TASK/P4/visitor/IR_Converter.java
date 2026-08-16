package visitor;

import syntaxtree.*;

import java.util.*;

public class IR_Converter extends GJDepthFirst<String, Void> {

    public int counter = 1000; // safe start value

    // Track when visiting the top-level StmtExp of a procedure body
    private boolean inTopLevelStmtExp = false;

    // print BEGIN/END for the outermost one
    private int stmtExpNesting = 0;

    private String fresh() {
        return "TEMP " + (counter++);
    }

    private boolean isTemp(String s) {
        return s != null && s.startsWith("TEMP ");
    }

    private String makeSimple(String expr) {
        if (isSimple(expr))
            return expr;
        String t = fresh();
        System.out.println("MOVE " + t + " " + expr);
        return t;
    }

    private boolean isSimple(String s) {
        if (s == null)
            return false;
        if (isTemp(s) || isInteger(s))
            return true;
        return !s.contains(" ");// if not contains space, it's a label
    }

    private String makeTemp(String expr) {
        if (isTemp(expr))
            return expr;
        String t = fresh();
        System.out.println("MOVE " + t + " " + expr);
        return t;
    }

    private boolean isInteger(String s) {
        return s != null && s.matches("[0-9]+");
    }

    /**
     * f0 -> "MAIN"
     * f1 -> StmtList()
     * f2 -> "END"
     * f3 -> ( Procedure() )*
     * f4 -> <EOF>
     */
    @Override
    public String visit(Goal n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        System.out.println("MAIN");
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        System.out.println("END");
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> ( ( Label() )? Stmt() )*
     */
    @Override
    public String visit(StmtList n, Void argu) {
        if (n.f0 != null && n.f0.present()) {
            for (Enumeration<Node> e = n.f0.elements(); e.hasMoreElements();) {
                Node seqNode = e.nextElement();
                if (!(seqNode instanceof NodeSequence)) {
                    seqNode.accept(this, argu);
                    continue;
                }
                NodeSequence seq = (NodeSequence) seqNode;
                if (seq.nodes.size() >= 2) {
                    Node first = seq.nodes.get(0);
                    Node second = seq.nodes.get(1);
                    // Print label only when it's the optional pre-statement label
                    if (first instanceof NodeOptional) {
                        NodeOptional opt = (NodeOptional) first;
                        if (opt.present() && opt.node instanceof Label) {
                            String label = ((Label) opt.node).accept(this, argu);
                            System.out.println(label);
                        }
                    }
                    if (second instanceof Stmt) {
                        ((Stmt) second).accept(this, argu);
                    } else {
                        second.accept(this, argu);
                    }
                } else {
                    seq.accept(this, argu);
                }
            }
        }
        return null;
    }

    /**
     * f0 -> Label()
     * f1 -> "["
     * f2 -> IntegerLiteral()
     * f3 -> "]"
     * f4 -> StmtExp()
     */
    @Override
    public String visit(Procedure n, Void argu) {
        String _ret = null;
        String labelName = n.f0.f0.toString();
        System.out.print(labelName);
        n.f1.accept(this, argu);
        System.out.print(" [ ");
        String intinstring = n.f2.accept(this, argu);
        System.out.print(intinstring);
        n.f3.accept(this, argu);
        System.out.println(" ]");
        boolean prev = inTopLevelStmtExp;
        inTopLevelStmtExp = true;
        n.f4.accept(this, argu);// for top level ,print BEGIN/END and RETURN SimpleExp
        inTopLevelStmtExp = prev;
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
     */
    @Override
    public String visit(Stmt n, Void argu) {
        String _ret = null;
        _ret = n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "NOOP"
     */
    @Override
    public String visit(NoOpStmt n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        System.out.println("NOOP");
        _ret = "NOOP";
        return _ret;
    }

    /**
     * f0 -> "ERROR"
     */
    @Override
    public String visit(ErrorStmt n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        System.out.println("ERROR");
        _ret = "ERROR";
        return _ret;
    }

    /**
     * f0 -> "CJUMP"
     * f1 -> Exp()
     * f2 -> Label()
     */
    @Override
    public String visit(CJumpStmt n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        String tempReg = makeTemp(n.f1.accept(this, argu));
        String labelName = n.f2.f0.toString();
        System.out.println("CJUMP " + tempReg + " " + labelName);
        return _ret;
    }

    /**
     * f0 -> "JUMP"
     * f1 -> Label()
     */
    @Override
    public String visit(JumpStmt n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        String labelName = n.f1.f0.toString();
        System.out.println("JUMP " + labelName);
        return _ret;
    }

    /**
     * f0 -> "HSTORE"
     * f1 -> Exp()
     * f2 -> IntegerLiteral()
     * f3 -> Exp()
     */
    @Override
    public String visit(HStoreStmt n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        String base = makeTemp(n.f1.accept(this, argu));
        String off = n.f2.accept(this, argu);
        String val = makeTemp(n.f3.accept(this, argu));
        System.out.println("HSTORE " + base + " " + off + " " + val);
        return _ret;
    }

    /**
     * f0 -> "HLOAD"
     * f1 -> Temp()
     * f2 -> Exp()
     * f3 -> IntegerLiteral()
     */
    @Override
    public String visit(HLoadStmt n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        String dst = n.f1.accept(this, argu);
        String base = makeTemp(n.f2.accept(this, argu));
        String off = n.f3.accept(this, argu);
        System.out.println("HLOAD " + dst + " " + base + " " + off);
        return _ret;
    }

    /**
     * f0 -> "MOVE"
     * f1 -> Temp()
     * f2 -> Exp()
     */
    @Override
    public String visit(MoveStmt n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        String dst = n.f1.accept(this, argu);
        String rhs = n.f2.accept(this, argu);
        // save into temp if rhs is call
        if (rhs != null && rhs.startsWith("CALL ")) {
            String t = fresh();
            System.out.println("MOVE " + t + " " + rhs);
            System.out.println("MOVE " + dst + " " + t);
        } else {
            System.out.println("MOVE " + dst + " " + rhs);
        }
        return _ret;
    }

    /**
     * f0 -> "PRINT"
     * f1 -> Exp()
     */
    @Override
    public String visit(PrintStmt n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        String e = n.f1.accept(this, argu);
        // save into temp if e is call
        if (e != null && e.startsWith("CALL ")) {
            String t = fresh();
            System.out.println("MOVE " + t + " " + e);
            System.out.println("PRINT " + t);
        } else {
            String se = makeSimple(e);
            System.out.println("PRINT " + se);
        }
        return _ret;
    }

    /**
     * f0 -> StmtExp()
     * | Call()
     * | HAllocate()
     * | BinOp()
     * | Temp()
     * | IntegerLiteral()
     * | Label()
     */
    public String visit(Exp n, Void argu) {
        // if result is a CALL, assign to TEMP and return that TEMP
        String result = n.f0.accept(this, argu);
        if (result != null && result.startsWith("CALL ")) {
            String t = fresh();
            System.out.println("MOVE " + t + " " + result);
            return t;
        }
        return result;
    }

    /**
     * f0 -> "BEGIN"
     * f1 -> StmtList()
     * f2 -> "RETURN"
     * f3 -> Exp()
     * f4 -> "END"
     */
    @Override
    public String visit(StmtExp n, Void argu) {
        boolean isTop = inTopLevelStmtExp && stmtExpNesting == 0;
        stmtExpNesting++;
        try {
            if (isTop) {
                // top level ,print BEGIN END
                n.f0.accept(this, argu);
                System.out.println("BEGIN");
                n.f1.accept(this, argu);
                n.f2.accept(this, argu);
                String ret = n.f3.accept(this, argu);
                String se = makeSimple(ret);
                System.out.println("RETURN " + se);
                n.f4.accept(this, argu);
                System.out.println("END");
                return null;
            } else {
                // print only stmts
                n.f1.accept(this, argu);
                String ret = n.f3.accept(this, argu);
                return makeSimple(ret);
            }
        } finally {
            stmtExpNesting--;
        }
    }

    /**
     * f0 -> "CALL"
     * f1 -> Exp()
     * f2 -> "("
     * f3 -> ( Exp() )*
     * f4 -> ")"
     */
    public String visit(Call n, Void argu) {
        n.f0.accept(this, argu);
        String func = makeSimple(n.f1.accept(this, argu));
        // make everything to temp
        List<String> argTemps = new ArrayList<>();// arguments list
        if (n.f3 != null && n.f3.present()) {
            Enumeration<Node> e = n.f3.elements();
            while (e.hasMoreElements()) {
                Node node = e.nextElement();
                // Each element of f3 is syntaxtree.Exp
                String a = ((Exp) node).accept(this, argu);
                String at = makeTemp(a);
                argTemps.add(at);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("CALL ").append(func).append(" (");
        if (!argTemps.isEmpty()) {
            sb.append(" ");
            sb.append(String.join(" ", argTemps));
            sb.append(" ");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * f0 -> "HALLOCATE"
     * f1 -> Exp()
     */
    @Override
    public String visit(HAllocate n, Void argu) {
        n.f0.accept(this, argu);
        String e = n.f1.accept(this, argu);
        String se = makeSimple(e);
        return "HALLOCATE " + se;
    }

    /**
     * f0 -> Operator()
     * f1 -> Exp()
     * f2 -> Exp()
     */
    @Override
    public String visit(BinOp n, Void argu) {
        String opName = n.f0.accept(this, argu);
        String left = makeTemp(n.f1.accept(this, argu));
        String right = makeSimple(n.f2.accept(this, argu));
        return opName + " " + left + " " + right;
    }

    /**
     * f0 -> "LE"
     * | "NE"
     * | "PLUS"
     * | "MINUS"
     * | "TIMES"
     * | "DIV"
     */
    @Override
    public String visit(Operator n, Void argu) {
        // return string
        Node choice = n.f0.choice;
        if (choice instanceof NodeToken) {
            return ((NodeToken) choice).toString();
        }
        return choice.toString();
    }

    /**
     * f0 -> "TEMP"
     * f1 -> IntegerLiteral()
     */
    @Override
    public String visit(Temp n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        String numString = n.f1.accept(this, argu);
        _ret = "TEMP " + numString;
        return _ret;
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    @Override
    public String visit(IntegerLiteral n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        _ret = n.f0.toString();// return integer as string
        return _ret;
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    @Override
    public String visit(Label n, Void argu) {
        String _ret = null;
        n.f0.accept(this, argu);
        _ret = n.f0.toString();// return label name as string
        // System.out.println(_ret);
        return _ret;
    }

}
