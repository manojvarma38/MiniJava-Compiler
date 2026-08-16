
# The 'N'Wizard Tournament  
### A Complete Multi-Pass Compiler for MiniJava

Welcome to the **'N'Wizard Tournament**.

This project is a full end-to-end compiler that takes a program written in a subset of Java and finally produces runnable **MIPS assembly**.  

Each stage of the compiler is a separate challenge in the tournament. The output of one challenge becomes the input of the next. Together they form a complete compiler pipeline.

---

## The Journey

```
MacroJava
   ↓  [P1] Trick the Dragon
MiniJava
   ↓  [P2] Decipher the Golden Egg
Type-checked MiniJava
   ↓  [P3] Get Your Wheezy from Merpeople
MiniIR
   ↓  [P4] The Unexpected Task
MicroIR
   ↓  [P5] Maze – Map the Virtual to Physical
MiniRA
   ↓  [P6] Flesh, Blood and Bone
MIPS Assembly
```

---

### P1: Trick the Dragon  
**MacroJava → MiniJava**

**Input:** MacroJava (MiniJava + C-style macros)  
**Output:** Pure MiniJava

In this challenge we face the Dragon of macros.  
MacroJava allows `#define` for both expressions and statements. Our job is to expand all macros and produce clean MiniJava code that a normal Java compiler (and the rest of our pipeline) can understand.

After this stage, no macros remain.

---

### P2: Decipher the Golden Egg  
**Type Checking**

**Input:** MiniJava  
**Output:** “Program type checked successfully” / “Type error” / “Symbol not found”

Before we can generate code, we must make sure the program is well-typed.  
We build a symbol table, handle inheritance, resolve method calls, and check every expression. Only programs that pass this stage are allowed to continue the tournament.

---

### P3: Get Your Wheezy from Merpeople  
**MiniJava → MiniIR**

**Input:** Type-checked MiniJava  
**Output:** MiniIR

Now we leave the high-level world and enter the intermediate representation.

**What is MiniIR?**
- Everything becomes temporaries (`TEMP 0`, `TEMP 1`…)
- Objects and arrays are just memory addresses
- `new` becomes `HALLOCATE`
- Method calls become `CALL`
- Control flow uses explicit `CJUMP` and `JUMP`
- Every class has a **vtable** (Virtual Method Table)

**How the vtable is stored:**

When we execute `new A()`:

1. Allocate a vtable and fill it with method addresses  
2. Allocate the object  
3. Store the vtable pointer at offset 0 of the object  
4. Fields start from offset 4 onwards

This is the stage where the object model is fully exposed.

---

### P4: The Unexpected Task  
**MiniIR → MicroIR**

**Input:** MiniIR  
**Output:** MicroIR

MiniIR still allows nested expressions and `BEGIN … END` blocks inside expressions.  
MicroIR is much stricter.

**Key Differences:**

| Feature                    | MiniIR                          | MicroIR                          |
|---------------------------|----------------------------------|----------------------------------|
| Nested expressions        | Allowed                          | Not allowed                      |
| `BEGIN…END` as expression | Allowed                          | Not allowed                      |
| Operands of operators     | Can be complex                   | Must be simple (`TEMP` or constant) |
| Style                     | Higher-level                     | Pure three-address code          |

In this stage we flatten every complex expression by introducing new temporaries. The result is a clean, simple intermediate form that is ready for register allocation.

---

### P5: Maze – Map the Virtual to Physical  
**MicroIR → MiniRA (Register Allocation)**

**Input:** MicroIR  
**Output:** MiniRA

Until now we had an **unlimited** number of temporaries.  
Real machines have only a fixed set of registers.

This is the most algorithmic stage of the tournament.

#### What we do:

1. **Build Control Flow Graph (CFG)** for every procedure  
2. Compute **def** and **use** sets for each instruction  
3. Perform iterative **Liveness Analysis** to compute `liveIn` and `liveOut`  
4. Convert liveness information into **Live Intervals**
5. Run **Linear Scan Register Allocation**

#### Live Intervals

A live interval for a temporary is the range of instructions  
`[start, end]` during which that temporary carries a value that may be used later.

Example:
```
TEMP 5 : [3, 17]
TEMP 8 : [10, 12]
```

We sort all intervals by their start point and then run the classic Linear Scan algorithm:

- Maintain an “active” list of intervals currently in registers  
- When we run out of registers, we spill the interval that ends farthest in the future  
- Spilled values are stored on the stack (`SPILLEDARG`)

#### MiniRA Features
- Real registers: `t0–t9`, `s0–s7`, `a0–a3`, `v0`, `v1`
- Stack slots for spilled values (`ALOAD` / `ASTORE`)
- Proper calling convention
- Procedure headers of the form:  
  `MethodName [num_args] [stack_slots] [max_call_args]`

---

### P6: Flesh, Blood and Bone  
**MiniRA → MIPS Assembly**

**Input:** MiniRA  
**Output:** Real MIPS assembly that can run on SPIM / MARS

This is the final transformation. We turn the register-allocated MiniRA into actual MIPS instructions.

**Important translations:**

| MiniRA              | MIPS                          |
|---------------------|-------------------------------|
| `MOVE t0 t1`        | `move $t0, $t1`               |
| `MOVE t0 42`        | `li $t0, 42`                  |
| `PLUS / MINUS / …`  | `add / sub / mul / …`         |
| `PRINT`             | system call (`$v0 = 1`)       |
| `HALLOCATE`         | system call (`$v0 = 9`)       |
| `CALL`              | `jal` / `jalr`                |
| `ALOAD / ASTORE`    | `lw / sw` relative to `$fp`   |
| Return              | `jr $ra`                      |

We also generate the standard prologue and epilogue for every function (save `$ra`, `$fp`, callee-saved registers, allocate stack frame, etc.).

At the end of this stage we finally have real machine code.

---

## Project Structure

```
Trick_the_Dragon/          → P1 (MacroJava → MiniJava)
Decipher_the_Golden_Egg/   → P2 (Type Checker)
Get_Your_Wheezy/           → P3 (MiniJava → MiniIR)
The_Unexpected_Task/       → P4 (MiniIR → MicroIR)
Maze_Map_the_Virtual/      → P5 (MicroIR → MiniRA)
Flesh_Blood_and_Bone/      → P6 (MiniRA → MIPS)
```

---

## How to Run the Full Pipeline

```bash
# P1
java P1 < input.macrojava > output.minijava

# P2
java P2 < output.minijava

# P3
java P3 < output.minijava > output.miniIR

# P4
java P4 < output.miniIR > output.microIR

# P5
java P5 < output.microIR > output.miniRA

# P6
java P6 < output.miniRA > output.s
```

You can then run the final `.s` file on a MIPS simulator (SPIM or MARS).

---
