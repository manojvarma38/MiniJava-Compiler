package visitor;
import java.util.*;
import syntaxtree.*;
import visitor.RegAllocator.AllocCtx;

public class Liveness {
    static void buildCFG(IRMain m) {
        buildCFGHelper(m.stmts, m.labelToIndex);
    }

    static void buildCFG(IRProc p) {
        buildCFGHelper(p.stmts, p.labelToIndex);
    }

    private static void buildCFGHelper(List<IRStmt> stmts, Map<String, Integer> labels) {
        int n = stmts.size();
        for (int i = 0; i < n; i++) {
            IRStmt s = stmts.get(i);
            // default fall-through successor
            if (i + 1 < n)
                s.succ.add(i + 1);
            if (s instanceof IRCJump) {
                Integer t = labels.get(((IRCJump) s).targetLabel);
                if (t != null)
                    s.succ.add(t);
            } else if (s instanceof IRJump) {
                s.succ.clear(); // jump has no fall-through
                Integer t = labels.get(((IRJump) s).targetLabel);
                if (t != null)
                    s.succ.add(t);
            }
        }
        // compute
        boolean changed;
        do {
            changed = false;
            for (int i = n - 1; i >= 0; i--) {
                IRStmt s = stmts.get(i);
                Set<Integer> inOld = new HashSet<>(s.in);
                Set<Integer> outOld = new HashSet<>(s.out);
                // out = union of in of successors
                s.out.clear();
                for (int j : s.succ) {
                    s.out.addAll(stmts.get(j).in);
                }
                // in = use U (out - def)
                s.in.clear();
                s.in.addAll(s.use);
                for (int v : new HashSet<>(s.out)) {
                    if (!s.def.contains(v))
                        s.in.add(v);
                }
                if (!s.in.equals(inOld) || !s.out.equals(outOld))
                    changed = true;
            }
        } while (changed);
    }

    static AllocationResult allocateForMain(IRMain m) {
        buildCFG(m);
        RegAllocator.AllocCtx ctx = new RegAllocator.AllocCtx();
        ctx.maxCallArgs = m.maxCallArgs;
        ctx.numArgs = 0;
        return RegAllocator.allocate(stmtsToBlocks(m.stmts), m.temps, ctx);
    }

    static AllocationResult allocateForProc(IRProc p) {
        buildCFG(p);
        RegAllocator.AllocCtx ctx = new RegAllocator.AllocCtx();
        ctx.maxCallArgs = p.maxCallArgs;
        ctx.numArgs = p.numArgs;
        return RegAllocator.allocate(stmtsToBlocks(p.stmts), p.temps, ctx);
    }

    private static List<IRStmt> stmtsToBlocks(List<IRStmt> stmts) {
        return stmts;
    }
}
