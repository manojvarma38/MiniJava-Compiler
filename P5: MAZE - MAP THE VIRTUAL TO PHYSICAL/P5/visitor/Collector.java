package visitor;

import java.util.*;
import syntaxtree.*;
import syntaxtree.*;

public class Collector extends GJNoArguDepthFirst<Void> {
    IRProgram prog = new IRProgram();

    public IRProgram run(Node root) {
        root.accept(this);
        return prog;
    }

    @Override
    public Void visit(Goal n) {
        // MAIN block
        IRMain main = new IRMain();
        collectStmtList(main.stmts, main.labelToIndex, main, n.f1);
        prog.main = main;

        // Procedures
        // iterate on all procedures
        if (n.f3 != null && n.f3.present()) {
            for (Node node : n.f3.nodes) {
                Procedure p = (Procedure) node;
                IRProc ip = new IRProc();
                ip.name = p.f0.f0.toString();
                ip.numArgs = Integer.parseInt(p.f2.f0.toString());
                // Body is a StmtExp
                StmtExp se = p.f4;
                collectStmtList(ip.stmts, ip.labelToIndex, ip, se.f1);
                ip.returnExp = readSimpleExp(se.f3);
                prog.procs.add(ip);
            }
        }
        return null;
    }

    private void collectStmtList(List<IRStmt> out, Map<String, Integer> labels, Object owner, StmtList list) {
        NodeListOptional items = list.f0;
        String pendingLabel = null;
        if (items != null && items.present()) {
            for (Node node : items.nodes) {
                NodeSequence seq = (NodeSequence) node;
                int idx = 0;
                Node first = seq.elementAt(idx);
                Label lbl = null;
                Stmt st;
                if (first instanceof NodeOptional) {
                    NodeOptional opt = (NodeOptional) first;
                    if (opt.present()) {
                        lbl = (Label) opt.node;
                    }
                    idx++;
                    st = (Stmt) seq.elementAt(idx);
                } else {
                    // No label
                    st = (Stmt) first;
                }
                if (lbl != null) {
                    pendingLabel = lbl.f0.toString();
                    labels.put(pendingLabel, out.size());
                }
                IRStmt ir = convertStmt(st);
                if (pendingLabel != null) {
                    ir.label = pendingLabel;
                    pendingLabel = null;
                }
                out.add(ir);
                // Track temps and calls
                if (owner instanceof IRMain) {
                    IRMain m = (IRMain) owner;
                    m.temps.addAll(ir.use);
                    m.temps.addAll(ir.def);
                    if (ir instanceof IRMove) {
                        m.temps.add(((IRMove) ir).dstTemp);
                    }
                    if (ir.isCall) {
                        int argc = ((IRMove) ir).exp instanceof IRCall ? ((IRCall) ((IRMove) ir).exp).argTemps.size()
                                : 0;
                        m.maxCallArgs = Math.max(m.maxCallArgs, argc);
                    }
                } else if (owner instanceof IRProc) {
                    IRProc pr = (IRProc) owner;
                    pr.temps.addAll(ir.use);
                    pr.temps.addAll(ir.def);
                    if (ir instanceof IRMove) {
                        pr.temps.add(((IRMove) ir).dstTemp);
                    }
                    if (ir.isCall) {
                        int argc = ((IRMove) ir).exp instanceof IRCall ? ((IRCall) ((IRMove) ir).exp).argTemps.size()
                                : 0;
                        pr.maxCallArgs = Math.max(pr.maxCallArgs, argc);
                    }
                }
            }
        }
    }

    private IRStmt convertStmt(Stmt st) {
        Node n = st.f0.choice;
        if (n instanceof NoOpStmt) {
            return new IRNoOp();
        } else if (n instanceof ErrorStmt) {
            return new IRError();
        } else if (n instanceof CJumpStmt) {
            CJumpStmt c = (CJumpStmt) n;
            int t = intFrom(((Temp) c.f1).f1);
            String lbl = ((Label) c.f2).f0.toString();
            return new IRCJump(t, lbl);
        } else if (n instanceof JumpStmt) {
            JumpStmt j = (JumpStmt) n;
            String lbl = ((Label) j.f1).f0.toString();
            return new IRJump(lbl);
        } else if (n instanceof HStoreStmt) {
            HStoreStmt h = (HStoreStmt) n;
            int base = intFrom(h.f1.f1);
            int off = intFrom(h.f2);
            int src = intFrom(h.f3.f1);
            return new IRHStore(base, off, src);
        } else if (n instanceof HLoadStmt) {
            HLoadStmt h = (HLoadStmt) n;
            int dst = intFrom(h.f1.f1);
            int base = intFrom(h.f2.f1);
            int off = intFrom(h.f3);
            return new IRHLoad(dst, base, off);
        } else if (n instanceof MoveStmt) {
            MoveStmt m = (MoveStmt) n;
            int dst = intFrom(m.f1.f1);
            IRExp exp = convertExp(m.f2);
            return new IRMove(dst, exp);
        } else if (n instanceof PrintStmt) {
            PrintStmt p = (PrintStmt) n;
            return new IRPrint(readSimpleExp(p.f1));
        }
        // never comes here
        throw new RuntimeException("Unknown stmt kind: " + n.getClass());
    }

    private IRExp convertExp(Exp e) {
        Node n = e.f0.choice;
        if (n instanceof Call) {
            Call c = (Call) n;
            IRSimpleExp tgt = readSimpleExp(c.f1);
            List<Integer> args = new ArrayList<>();
            if (c.f3 != null && c.f3.present()) {
                for (Node ti : c.f3.nodes) {
                    Temp t = (Temp) ti;
                    args.add(intFrom(t.f1));
                }
            }
            return new IRCall(tgt, args);
        } else if (n instanceof HAllocate) {
            HAllocate h = (HAllocate) n;
            return new IRHAllocate(readSimpleExp(h.f1));
        } else if (n instanceof BinOp) {
            BinOp b = (BinOp) n;
            String op = b.f0.f0.choice.toString();
            int lt = intFrom(b.f1.f1);
            IRSimpleExp r = readSimpleExp(b.f2);
            return new IRBinOp(op, lt, r);
        } else if (n instanceof SimpleExp) {
            return new IRSimple(readSimpleExp((SimpleExp) n));
        }
        throw new RuntimeException("Unknown exp kind: " + n.getClass());
    }

    private IRSimpleExp readSimpleExp(SimpleExp se) {
        Node n = se.f0.choice;
        if (n instanceof Temp) {
            return new IRSimpleTemp(intFrom(((Temp) n).f1));
        } else if (n instanceof IntegerLiteral) {
            return new IRSimpleInt(intFrom((IntegerLiteral) n));
        } else if (n instanceof Label) {
            return new IRSimpleLabel(((Label) n).f0.toString());
        }
        throw new RuntimeException("Unknown SimpleExp kind");
    }

    // Wrap a IRSimpleExp as IRExp when needed
    static class IRSimple extends IRExp {
        IRSimpleExp inner;

        IRSimple(IRSimpleExp s) {
            this.inner = s;
        }

        @Override
        Set<Integer> uses() {
            return inner.uses();
        }
    }

    private int intFrom(IntegerLiteral i) {
        return Integer.parseInt(i.f0.toString());
    }
}
