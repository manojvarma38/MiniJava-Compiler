package visitor;

import java.io.*;
import java.util.*;
import syntaxtree.*;

public class Emitter {
    private static final List<String> A_REGS = Arrays.asList("a0", "a1", "a2", "a3");
    private Set<String> procNames;

    public void emit(IRProgram prog, PrintStream out) {
        // Collect procedure names for global labels
        procNames = new HashSet<>();
        for (IRProc p : prog.procs)
            procNames.add(p.name);

        AllocationResult mainAlloc = Liveness.allocateForMain(prog.main);
        Map<IRProc, AllocationResult> procAllocs = new LinkedHashMap<>();
        for (IRProc p : prog.procs)
            procAllocs.put(p, Liveness.allocateForProc(p));

        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
        emitMain(prog.main, mainAlloc, pw);
        for (IRProc p : prog.procs)
            emitProc(p, procAllocs.get(p), pw);
        pw.flush();
    }

    private void emitMain(IRMain m, AllocationResult a, PrintWriter pw) {
        // Header
        pw.printf("MAIN [%d] [%d] [%d]%n", 0, a.stackSlotsTotal, m.maxCallArgs);
        // Prologue: save used s-regs
        List<String> usedS = new ArrayList<>(a.usedSRegs);
        usedS.sort(Comparator.comparingInt(this::sIndex));
        int base = a.incomingExtra + a.outgoingExtra;
        for (int i = 0; i < usedS.size(); i++) {
            int slot = base + i;
            pw.printf("ASTORE SPILLEDARG %d %s%n", slot, usedS.get(i));
        }

        // Body
        for (IRStmt s : m.stmts)
            emitStmt("MAIN", s, a, pw);

        // Epilogue: restore s-regs
        for (int i = usedS.size() - 1; i >= 0; i--) {
            int slot = base + i;
            pw.printf("ALOAD %s SPILLEDARG %d%n", usedS.get(i), slot);
        }
        pw.println("END");
        pw.printf("// %s%n", a.spilledAny ? "SPILLED" : "NOTSPILLED");
    }

    private void emitProc(IRProc p, AllocationResult a, PrintWriter pw) {
        pw.printf("%s [%d] [%d] [%d]%n", p.name, p.numArgs, a.stackSlotsTotal, p.maxCallArgs);
        // Prologue: save used s-regs
        List<String> usedS = new ArrayList<>(a.usedSRegs);
        usedS.sort(Comparator.comparingInt(this::sIndex));
        int base = a.incomingExtra + a.outgoingExtra;
        for (int i = 0; i < usedS.size(); i++) {
            int slot = base + i;
            pw.printf("ASTORE SPILLEDARG %d %s%n", slot, usedS.get(i));
        }

        // Initialize formal parameters TEMP 0..numArgs-1 if present
        for (int i = 0; i < p.numArgs; i++) {
            String srcReg;
            if (i < 4)
                srcReg = A_REGS.get(i);
            else {
                int spilledIndex = i - 4;
                srcReg = freeScratch(new HashSet<>());
                pw.printf("ALOAD %s SPILLEDARG %d%n", srcReg, spilledIndex);
            }
            writeAssignTemp(i, srcReg, a, pw);
        }

        // Body
        for (IRStmt s : p.stmts)
            emitStmt(p.name, s, a, pw);

        // Return value
        emitMoveToReg("v0", p.returnExp, p.name, a, pw);

        // Epilogue restore
        for (int i = usedS.size() - 1; i >= 0; i--) {
            int slot = base + i;
            pw.printf("ALOAD %s SPILLEDARG %d%n", usedS.get(i), slot);
        }
        pw.println("END");
        pw.printf("// %s%n", a.spilledAny ? "SPILLED" : "NOTSPILLED");
    }

    private void emitStmt(String scope, IRStmt s, AllocationResult a, PrintWriter pw) {
        if (s.label != null)
            pw.println(mangle(scope, s.label));
        Set<String> scratchUsed = new HashSet<>();
        if (s instanceof IRNoOp) {
            pw.println("NOOP");
        } else if (s instanceof IRError) {
            pw.println("ERROR");
        } else if (s instanceof IRCJump) {
            IRCJump c = (IRCJump) s;
            String r = ensureRegForTemp(c.condTemp, a, pw, scratchUsed);
            pw.printf("CJUMP %s %s%n", r, mangle(scope, c.targetLabel));
        } else if (s instanceof IRJump) {
            IRJump j = (IRJump) s;
            pw.printf("JUMP %s%n", mangle(scope, j.targetLabel));
        } else if (s instanceof IRHStore) {
            IRHStore h = (IRHStore) s;
            String baseR = ensureRegForTemp(h.baseTemp, a, pw, scratchUsed);
            String srcR = ensureAnotherRegForTemp(h.srcTemp, a, pw, scratchUsed);
            pw.printf("HSTORE %s %d %s%n", baseR, h.offset, srcR);
        } else if (s instanceof IRHLoad) {
            IRHLoad h = (IRHLoad) s;
            boolean destSpilled = !a.tempToReg.containsKey(h.dstTemp);
            String baseR = ensureRegForTemp(h.baseTemp, a, pw, scratchUsed);
            if (!destSpilled) {
                String d = a.tempToReg.get(h.dstTemp);
                pw.printf("HLOAD %s %s %d%n", d, baseR, h.offset);
            } else {
                String tmp = freeScratch(scratchUsed);
                pw.printf("HLOAD %s %s %d%n", tmp, baseR, h.offset);
                int slot = a.tempToSpillSlot.get(h.dstTemp);
                pw.printf("ASTORE SPILLEDARG %d %s%n", slot, tmp);
            }
        } else if (s instanceof IRMove) {
            IRMove m = (IRMove) s;
            boolean destSpilled = !a.tempToReg.containsKey(m.dstTemp);
            if (m.exp instanceof IRCall) {
                emitCall((IRCall) m.exp, scope, a, pw, scratchUsed);
                if (!destSpilled) {
                    pw.printf("MOVE %s v0%n", a.tempToReg.get(m.dstTemp));
                } else {
                    int slot = a.tempToSpillSlot.get(m.dstTemp);
                    pw.printf("ASTORE SPILLEDARG %d v0%n", slot);
                }
            } else if (m.exp instanceof IRHAllocate) {
                // allocate size+4
                IRSimpleExp sz = ((IRHAllocate) m.exp).size;
                if (!destSpilled) {
                    String d = a.tempToReg.get(m.dstTemp);
                    if (sz instanceof IRSimpleInt) {
                        int val = ((IRSimpleInt) sz).value + 4;
                        pw.printf("MOVE %s HALLOCATE %d%n", d, val);
                    } else {
                        String sizeReg = ensureSimpleExp(sz, scope, a, pw, scratchUsed);
                        String sreg = freeScratch(scratchUsed);
                        pw.printf("MOVE %s PLUS %s 4%n", sreg, sizeReg);
                        pw.printf("MOVE %s HALLOCATE %s%n", d, sreg);
                    }
                } else {
                    // destination is spilled: allocate into a scratch then store
                    String tmp = freeScratch(scratchUsed);
                    if (sz instanceof IRSimpleInt) {
                        int val = ((IRSimpleInt) sz).value + 4;
                        pw.printf("MOVE %s HALLOCATE %d%n", tmp, val);
                    } else {
                        String sizeReg = ensureSimpleExp(sz, scope, a, pw, scratchUsed);
                        String sreg = freeScratch(scratchUsed);
                        pw.printf("MOVE %s PLUS %s 4%n", sreg, sizeReg);
                        pw.printf("MOVE %s HALLOCATE %s%n", tmp, sreg);
                    }
                    int slot = a.tempToSpillSlot.get(m.dstTemp);
                    pw.printf("ASTORE SPILLEDARG %d %s%n", slot, tmp);
                }
            } else if (m.exp instanceof IRBinOp) {
                IRBinOp b = (IRBinOp) m.exp;
                String left = ensureRegForTemp(b.leftTemp, a, pw, scratchUsed);
                String rightSE = ensureSimpleExp(b.right, scope, a, pw, scratchUsed);
                if (!destSpilled) {
                    String d = a.tempToReg.get(m.dstTemp);
                    pw.printf("MOVE %s %s %s %s%n", d, b.op, left, rightSE);
                } else {
                    String tmp = freeScratch(scratchUsed);
                    pw.printf("MOVE %s %s %s %s%n", tmp, b.op, left, rightSE);
                    int slot = a.tempToSpillSlot.get(m.dstTemp);
                    pw.printf("ASTORE SPILLEDARG %d %s%n", slot, tmp);
                }
            } else {
                // IRSimple wrapped
                IRSimpleExp se = ((Collector.IRSimple) m.exp).inner;
                if (!destSpilled) {
                    String d = a.tempToReg.get(m.dstTemp);
                    String rhs = ensureSimpleExp(se, scope, a, pw, scratchUsed);
                    pw.printf("MOVE %s %s%n", d, rhs);
                } else {
                    String tmp = freeScratch(scratchUsed);
                    String rhs = ensureSimpleExp(se, scope, a, pw, scratchUsed);
                    pw.printf("MOVE %s %s%n", tmp, rhs);
                    int slot = a.tempToSpillSlot.get(m.dstTemp);
                    pw.printf("ASTORE SPILLEDARG %d %s%n", slot, tmp);
                }
            }
        } else if (s instanceof IRPrint) {
            IRPrint pr = (IRPrint) s;
            String se = ensureSimpleExp(pr.value, scope, a, pw, scratchUsed);
            pw.printf("PRINT %s%n", se);
        } else {
            throw new RuntimeException("Unknown stmt kind");
        }
    }

    private void emitCall(IRCall c, String scope, AllocationResult a, PrintWriter pw, Set<String> scratchUsed) {
        // Move first 4 args to a-regs
        for (int i = 0; i < Math.min(4, c.argTemps.size()); i++) {
            String r = ensureRegForTemp(c.argTemps.get(i), a, pw, scratchUsed);
            pw.printf("MOVE %s %s%n", A_REGS.get(i), r);
        }
        // Remaining via stack
        for (int i = 4; i < c.argTemps.size(); i++) {
            String r = ensureRegForTemp(c.argTemps.get(i), a, pw, scratchUsed);
            pw.printf("PASSARG %d %s%n", i - 3, r);
        }
        // Target
        String tgt = ensureSimpleExp(c.target, scope, a, pw, scratchUsed);
        pw.printf("CALL %s%n", tgt);
    }

    private String ensureSimpleExp(IRSimpleExp e, String scope, AllocationResult a, PrintWriter pw,
            Set<String> scratchUsed) {
        if (e instanceof IRSimpleInt)
            return Integer.toString(((IRSimpleInt) e).value);
        if (e instanceof IRSimpleLabel) {
            String lbl = ((IRSimpleLabel) e).label;
            return procNames.contains(lbl) ? lbl : mangle(scope, lbl);
        }
        if (e instanceof IRSimpleTemp) {
            return ensureRegForTemp(((IRSimpleTemp) e).temp, a, pw, scratchUsed);
        }
        throw new RuntimeException("Unknown simple exp");
    }

    private void emitMoveToReg(String dstReg, IRSimpleExp e, String scope, AllocationResult a, PrintWriter pw) {
        Set<String> scratchUsed = new HashSet<>();
        String rhs = ensureSimpleExp(e, scope, a, pw, scratchUsed);
        pw.printf("MOVE %s %s%n", dstReg, rhs);
    }

    private void writeAssignTemp(int temp, String srcReg, AllocationResult a, PrintWriter pw) {
        if (a.tempToReg.containsKey(temp)) {
            String d = a.tempToReg.get(temp);
            if (!d.equals(srcReg))
                pw.printf("MOVE %s %s%n", d, srcReg);
        } else if (a.tempToSpillSlot.containsKey(temp)) {
            int slot = a.tempToSpillSlot.get(temp);
            pw.printf("ASTORE SPILLEDARG %d %s%n", slot, srcReg);
        } else {
            // Temp not used; nothing to do
        }
    }

    private String ensureRegForTemp(int temp, AllocationResult a, PrintWriter pw, Set<String> scratchUsed) {
        String r = a.tempToReg.get(temp);
        if (r != null)
            return r;
        // Spilled: load into scratch
        String scratch = freeScratch(scratchUsed);
        int slot = a.tempToSpillSlot.get(temp);
        pw.printf("ALOAD %s SPILLEDARG %d%n", scratch, slot);
        return scratch;
    }

    private String ensureAnotherRegForTemp(int temp, AllocationResult a, PrintWriter pw, Set<String> scratchUsed) {
        String r = a.tempToReg.get(temp);
        if (r != null)
            return r;
        // Needs another scratch even if one used already
        String scratch = freeScratch(scratchUsed);
        int slot = a.tempToSpillSlot.get(temp);
        pw.printf("ALOAD %s SPILLEDARG %d%n", scratch, slot);
        return scratch;
    }

    // lets use free reg first
    private String freeScratch(Set<String> used) {
        if (!used.contains("v1")) {
            used.add("v1");
            return "v1";
        }
        // Avoid using v0 before calls' result, but use here as second scratch
        if (!used.contains("v0")) {
            used.add("v0");
            return "v0";
        }
        // Fallback to t9 if both taken
        if (!used.contains("t9")) {
            used.add("t9");
            return "t9";
        }
        // As a last resort, t8
        if (!used.contains("t8")) {
            used.add("t8");
            return "t8";
        }
        return "t7"; // hope available
    }

    private String mangle(String scope, String label) {
        return scope + "_" + label;
    }

    private int sIndex(String s) {
        return Integer.parseInt(s.substring(1));
    }
}
