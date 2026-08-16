package visitor;

import java.util.*;
import syntaxtree.*;

public class IRProgram {
    public IRMain main;
    public List<IRProc> procs = new ArrayList<>();
}

class IRMain {
    public List<IRStmt> stmts = new ArrayList<>();
    public Map<String, Integer> labelToIndex = new HashMap<>();
    public int maxCallArgs = 0; // max args of calls in main
    public Set<Integer> temps = new HashSet<>();
}

class IRProc {
    public String name;
    public int numArgs; // from microIR header
    public List<IRStmt> stmts = new ArrayList<>();
    public Map<String, Integer> labelToIndex = new HashMap<>();
    public int maxCallArgs = 0; // max args of calls in body
    public Set<Integer> temps = new HashSet<>();
    public IRSimpleExp returnExp; // for procedure only
}

// Base classes
abstract class IRStmt {
    public String label;
    // Successors for CFG
    public List<Integer> succ = new ArrayList<>();// to form the graph
    // Liveness info
    public Set<Integer> use = new HashSet<>();
    public Set<Integer> def = new HashSet<>();
    public Set<Integer> in = new HashSet<>();
    public Set<Integer> out = new HashSet<>();
    // if this stmt is a call
    public boolean isCall = false;
}

class IRNoOp extends IRStmt {
}

class IRError extends IRStmt {
}

class IRCJump extends IRStmt {
    public int condTemp; // TEMP id
    public String targetLabel;

    public IRCJump(int t, String lbl) {
        this.condTemp = t;
        this.targetLabel = lbl;
        this.use.add(t);
    }
}

class IRJump extends IRStmt {
    public String targetLabel;

    public IRJump(String lbl) {
        this.targetLabel = lbl;
    }
}

class IRHStore extends IRStmt {
    public int baseTemp;
    public int offset;
    public int srcTemp;

    public IRHStore(int base, int off, int src) {
        this.baseTemp = base;
        this.offset = off;
        this.srcTemp = src;
        this.use.add(base);
        this.use.add(src);
    }
}

class IRHLoad extends IRStmt {
    public int dstTemp;
    public int baseTemp;
    public int offset;

    public IRHLoad(int dst, int base, int off) {
        this.dstTemp = dst;
        this.baseTemp = base;
        this.offset = off;
        this.def.add(dst);
        this.use.add(base);
    }
}

class IRMove extends IRStmt {
    public int dstTemp;
    public IRExp exp;

    public IRMove(int dst, IRExp e) {
        this.dstTemp = dst;
        this.exp = e;
        this.def.add(dst);
        this.use.addAll(e.uses());
        if (e instanceof IRCall)
            this.isCall = true;
    }
}

class IRPrint extends IRStmt {
    public IRSimpleExp value;

    public IRPrint(IRSimpleExp v) {
        this.value = v;
        this.use.addAll(v.uses());
    }
}

// Expressions (only appear on right-hand side of MOVE)
abstract class IRExp {
    abstract Set<Integer> uses();
}

class IRCall extends IRExp {
    public IRSimpleExp target;
    public List<Integer> argTemps = new ArrayList<>();

    public IRCall(IRSimpleExp tgt, List<Integer> args) {
        this.target = tgt;
        this.argTemps.addAll(args);
    }

    @Override
    Set<Integer> uses() {
        Set<Integer> s = new HashSet<>();
        s.addAll(target.uses());
        s.addAll(argTemps);
        return s;
    }
}

class IRHAllocate extends IRExp {
    public IRSimpleExp size;

    public IRHAllocate(IRSimpleExp s) {
        this.size = s;
    }

    @Override
    Set<Integer> uses() {
        return size.uses();
    }
}

class IRBinOp extends IRExp {
    public String op; // LE, NE, PLUS, MINUS, TIMES, DIV
    public int leftTemp; // TEMP id
    public IRSimpleExp right;

    public IRBinOp(String op, int leftTemp, IRSimpleExp right) {
        this.op = op;
        this.leftTemp = leftTemp;
        this.right = right;
    }

    @Override
    Set<Integer> uses() {
        Set<Integer> s = new HashSet<>();
        s.add(leftTemp);
        s.addAll(right.uses());
        return s;
    }
}

// SimpleExp used in both statements and expressions
abstract class IRSimpleExp {
    abstract Set<Integer> uses();
}

class IRSimpleTemp extends IRSimpleExp {
    public int temp;

    public IRSimpleTemp(int t) {
        this.temp = t;
    }

    @Override
    Set<Integer> uses() {
        return new HashSet<>(java.util.Arrays.asList(temp));
    }
}

class IRSimpleInt extends IRSimpleExp {
    public int value;

    public IRSimpleInt(int v) {
        this.value = v;
    }

    @Override
    Set<Integer> uses() {
        return java.util.Collections.emptySet();
    }
}

class IRSimpleLabel extends IRSimpleExp {
    public String label;

    public IRSimpleLabel(String l) {
        this.label = l;
    }

    @Override
    Set<Integer> uses() {
        return java.util.Collections.emptySet();
    }
}

// Allocation result per procedure/main
class AllocationResult {
    public Map<Integer, String> tempToReg = new HashMap<>(); // TEMP id -> reg (t0, s3,....)
    public Map<Integer, Integer> tempToSpillSlot = new HashMap<>(); // TEMP id -> SPILLEDARG slot index
    public Set<String> usedSRegs = new HashSet<>(); // s-registers used (for callee-save)
    public int spillSlots = 0; // number of spill slots used
    public boolean spilledAny = false;
    public int incomingExtra = 0; // > max(0, numArgs - 4)
    public int outgoingExtra = 0; // > max(0, maxCallArgs - 4)
    public int savedSCount = 0; // usedSRegs size
    public int stackSlotsTotal = 0; // header [2]
}
