package visitor;

import java.util.*;
import syntaxtree.*;

public class RegAllocator {
    static class AllocCtx {
        int numArgs;
        int maxCallArgs;
    }

    private static final List<String> S_REGS = Arrays.asList("s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7");
    private static final List<String> T_REGS = Arrays.asList("t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8",
            "t9");

    static class Interval {
        int temp;
        int start = Integer.MAX_VALUE;
        int end = -1;
        boolean callLive = false; // live across a call
        boolean everUsed = false;
        boolean mustReg = false; // must not be spilled if used as heap base
    }

    public static AllocationResult allocate(List<IRStmt> stmts, Set<Integer> temps, AllocCtx ctx) {
        // Build intervals
        Map<Integer, Interval> ivs = new HashMap<>();
        for (int t : temps) {
            Interval iv = new Interval();
            iv.temp = t;
            ivs.put(t, iv);
        }
        for (int i = 0; i < stmts.size(); i++) {
            IRStmt s = stmts.get(i);
            Set<Integer> appear = new HashSet<>();
            appear.addAll(s.use);
            appear.addAll(s.def);
            appear.addAll(s.in);
            appear.addAll(s.out);
            for (int t : appear) {
                Interval iv = ivs.computeIfAbsent(t, k -> {
                    Interval v = new Interval();
                    v.temp = k;
                    return v;
                });
                iv.start = Math.min(iv.start, i);
                iv.end = Math.max(iv.end, i);
                iv.everUsed = true;
            }
            if (s.isCall) {
                for (int t : s.in)
                    if (s.out.contains(t)) {
                        Interval iv = ivs.get(t);
                        if (iv != null)
                            iv.callLive = true;
                    }
            }
            // Mark base temps of heap ops as mustReg
            if (s instanceof IRHLoad) {
                int base = ((IRHLoad) s).baseTemp;
                Interval iv = ivs.computeIfAbsent(base, k -> {
                    Interval v = new Interval();
                    v.temp = k;
                    return v;
                });
                iv.mustReg = true;
                iv.everUsed = true;
                iv.start = Math.min(iv.start, i);
                iv.end = Math.max(iv.end, i);
            } else if (s instanceof IRHStore) {
                int base = ((IRHStore) s).baseTemp;
                Interval iv = ivs.computeIfAbsent(base, k -> {
                    Interval v = new Interval();
                    v.temp = k;
                    return v;
                });
                iv.mustReg = true;
                iv.everUsed = true;
                iv.start = Math.min(iv.start, i);
                iv.end = Math.max(iv.end, i);
            }
        }

        // Force temps that are results of HALLOCATE to use s-registers (they hold heap
        // pointers)
        Set<Integer> mustBeS = new HashSet<>();
        for (IRStmt s : stmts) {
            if (s instanceof IRMove) {
                IRMove m = (IRMove) s;
                if (m.exp instanceof IRHAllocate) {
                    mustBeS.add(m.dstTemp);
                }
            }
        }

        // change
        boolean changed;
        do {
            changed = false;
            for (IRStmt s : stmts) {
                if (s instanceof IRMove) {
                    IRMove m = (IRMove) s;
                    if (m.exp instanceof Collector.IRSimple) {
                        IRSimpleExp se = ((Collector.IRSimple) m.exp).inner;
                        if (se instanceof IRSimpleTemp) {
                            Interval dstIv = ivs.get(m.dstTemp);
                            if (dstIv != null && dstIv.mustReg) {
                                int src = ((IRSimpleTemp) se).temp;
                                Interval srcIv = ivs.computeIfAbsent(src, k -> {
                                    Interval v = new Interval();
                                    v.temp = k;
                                    return v;
                                });
                                if (!srcIv.mustReg) {
                                    srcIv.mustReg = true;
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
        } while (changed);
        //

        // Prepare allocator pools
        List<Interval> list = new ArrayList<>(ivs.values());
        // Remove never-used
        list.removeIf(iv -> !iv.everUsed);
        list.sort(Comparator.comparingInt(iv -> iv.start));

        Map<Integer, String> tempToReg = new HashMap<>();
        Map<Integer, Integer> tempToSpill = new HashMap<>();
        Set<String> usedS = new HashSet<>();
        int spillCount = 0;

        // Active lists for s and t regs
        List<Interval> activeS = new ArrayList<>();
        List<Interval> activeT = new ArrayList<>();

        for (Interval iv : list) {
            if (iv.callLive || mustBeS.contains(iv.temp)) {
                expireOld(iv, activeS);
                String reg = firstFree(S_REGS, activeS, tempToReg);
                if (reg != null) {
                    tempToReg.put(iv.temp, reg);
                    usedS.add(reg);
                    activeS.add(iv);
                } else {
                    // Try evict a non-must interval from S
                    Interval victim = pickEviction(activeS, true);
                    if (victim != null) {
                        // Spill victim
                        tempToSpill.put(victim.temp, spillCount++);
                        tempToReg.remove(victim.temp);
                        activeS.remove(victim);
                        // Assign its register to current
                        String vreg = firstFree(S_REGS, activeS, tempToReg);
                        if (vreg == null) {
                            tempToSpill.put(iv.temp, spillCount++);
                        } else {
                            tempToReg.put(iv.temp, vreg);
                            usedS.add(vreg);
                            activeS.add(iv);
                        }
                    } else {
                        tempToSpill.put(iv.temp, spillCount++);
                    }
                }
            } else {
                expireOld(iv, activeT);
                String reg = firstFree(T_REGS, activeT, tempToReg);
                if (reg != null) {
                    tempToReg.put(iv.temp, reg);
                    activeT.add(iv);
                } else {

                    expireOld(iv, activeS);
                    String sreg = firstFree(S_REGS, activeS, tempToReg);
                    if (sreg != null) {
                        tempToReg.put(iv.temp, sreg);
                        usedS.add(sreg);
                        activeS.add(iv);
                    } else {
                        if (iv.mustReg) {
                            // Try evict from T first
                            Interval victim = pickEviction(activeT, true);
                            if (victim != null) {
                                tempToSpill.put(victim.temp, spillCount++);
                                tempToReg.remove(victim.temp);
                                activeT.remove(victim);
                                String treg = firstFree(T_REGS, activeT, tempToReg);
                                if (treg == null) {
                                    tempToSpill.put(iv.temp, spillCount++);
                                } else {
                                    tempToReg.put(iv.temp, treg);
                                    activeT.add(iv);
                                }
                            } else {
                                // Try evict from S
                                victim = pickEviction(activeS, true);
                                if (victim != null) {
                                    tempToSpill.put(victim.temp, spillCount++);
                                    tempToReg.remove(victim.temp);
                                    activeS.remove(victim);
                                    String sFreed = firstFree(S_REGS, activeS, tempToReg);
                                    if (sFreed == null) {
                                        tempToSpill.put(iv.temp, spillCount++);
                                    } else {
                                        tempToReg.put(iv.temp, sFreed);
                                        usedS.add(sFreed);
                                        activeS.add(iv);
                                    }
                                } else {
                                    tempToSpill.put(iv.temp, spillCount++);
                                }
                            }
                        } else {
                            tempToSpill.put(iv.temp, spillCount++);
                        }
                    }
                }
            }
            // keep active lists sorted by end
            activeS.sort(Comparator.comparingInt(a -> a.end));
            activeT.sort(Comparator.comparingInt(a -> a.end));
        }

        AllocationResult ar = new AllocationResult();
        ar.tempToReg = tempToReg;
        ar.usedSRegs = usedS;
        ar.spilledAny = !tempToSpill.isEmpty();
        ar.savedSCount = usedS.size();
        ar.incomingExtra = Math.max(0, ctx.numArgs - 4);
        ar.outgoingExtra = Math.max(0, ctx.maxCallArgs - 4);
        // Spill slots start after incoming+outgoing+savedS
        int base = ar.incomingExtra + ar.outgoingExtra;
        Map<Integer, Integer> tempToSpillAbs = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : tempToSpill.entrySet()) {
            tempToSpillAbs.put(e.getKey(), base + ar.savedSCount + e.getValue());
        }
        ar.tempToSpillSlot = tempToSpillAbs;
        ar.spillSlots = spillCount;
        ar.stackSlotsTotal = ar.incomingExtra + ar.outgoingExtra + ar.savedSCount + ar.spillSlots;
        return ar;
    }

    private static void expireOld(Interval cur, List<Interval> active) {
        Iterator<Interval> it = active.iterator();
        while (it.hasNext()) {
            Interval iv = it.next();
            if (iv.end < cur.start) {
                it.remove();
            }
        }
    }

    private static String firstFree(List<String> regs, List<Interval> active, Map<Integer, String> tempToReg) {
        Set<String> used = new HashSet<>();
        for (Interval iv : active) {
            String r = tempToReg.get(iv.temp);
            if (r != null)
                used.add(r);
        }
        for (String r : regs)
            if (!used.contains(r))
                return r;
        return null;
    }

    private static Interval pickEviction(List<Interval> active, boolean preferNonMust) {
        Interval bestNonMust = null, bestAny = null;
        int farNonMust = -1, farAny = -1;
        for (Interval iv : active) {
            if (!iv.mustReg && iv.end > farNonMust) {
                farNonMust = iv.end;
                bestNonMust = iv;
            }
            if (iv.end > farAny) {
                farAny = iv.end;
                bestAny = iv;
            }
        }
        if (preferNonMust && bestNonMust != null)
            return bestNonMust;
        return bestAny;
    }
}
