package visitor;

import syntaxtree.*;
import visitor.IRCodeEmitter.Expr;
import visitor.IRCodeEmitter.Temp;

import java.util.*;

public class IRCodeEmitter extends GJDepthFirst<IRCodeEmitter.Expr, Void> {

    // helpers
    public static class Temp {
        public int next = 1000; // start from 1000 to avoid conflict with sample code temps

        public int getNextTemp() {
            return next++;
        }
    }

    public static class Label {
        public int next = 0;

        public String fresh(String base) {
            return base + (next++);
        }
    }

    public static class Expr {
        public String ir;
        public String type;

        public Expr(String ir, String type) {
            this.ir = ir;
            this.type = type;
        }
    }

    //

    private ProgInfo prog_info;
    private Temp tempGen = new Temp();
    private Label labelGen = new Label();

    // lambda
    private static class LambdaContext {
        String label;
        String paramName;
        LinkedHashMap<String, Integer> indexInLambda = new LinkedHashMap<>();
        int closureParamTemp = -1; // local temp holding closure pointer within lambda
        String lambdaClass;
        String lambdaMethod;
    }

    // to print later
    private Deque<StringBuilder> printLater = new ArrayDeque<>();
    private List<String> lambdaFunctions = new ArrayList<>();
    private boolean inLambda = false;
    private LambdaContext currentLambda = null;

    // contexts
    private String currentClass = null;
    private String currentMethod = null;
    private LinkedHashMap<String, Integer> nameToTemp = null;// local/param name -> temp number in current method (TEMP
                                                             // 0 reserved for 'this')

    public IRCodeEmitter(ProgInfo pi) {
        this.prog_info = pi;
    }

    private int newTemp() {
        return tempGen.getNextTemp();
    }

    private String newLabel(String base) {
        return labelGen.fresh(base);
    }

    private void emit(String s) {
        if (printLater.isEmpty()) {
            System.out.println(s);
        } else {
            printLater.peek().append(s).append('\n');
        }
    }

    private void emitLabelLine(String lab) {
        emit(lab);
    } // label on its own line

    private String makeTemp(String simple) {
        if (simple.startsWith("TEMP ")) {
            return simple;
        } else {
            int t = newTemp();
            emit("MOVE TEMP " + t + " " + simple);
            return "TEMP " + t;
        }
    }

    private Expr getIdentifierExpr(String id) {
        // 'this'
        if ("this".equals(id)) {
            return new Expr("TEMP 0", currentClass);
        }

        // Local/param in current context (includes lambda params)
        if (nameToTemp != null && nameToTemp.containsKey(id)) {
            // use nameToTemp directly
            if (inLambda) {
                return new Expr("TEMP " + nameToTemp.get(id), "unknown");
            }

            // Otherwise, get type info from ProgInfo
            String type = null;
            ProgInfo.MethodInfo mi = prog_info.getMethodInfo(currentClass, currentMethod);
            if (mi != null) {
                type = mi.locals.get(id);
                if (type == null)
                    type = mi.method_params.get(id);
            }
            if (type == null)
                type = "unknown";
            return new Expr("TEMP " + nameToTemp.get(id), type);
        }

        // Class name reference
        if (prog_info.classes.containsKey(id)) {
            return new Expr(id, id);
        }

        // inside lambda: treat unknown identifiers as captured from the cur scope
        if (inLambda && currentLambda != null) {
            Integer idx = currentLambda.indexInLambda.get(id);
            if (idx == null) {
                idx = currentLambda.indexInLambda.size() + 1; // start at 1
                currentLambda.indexInLambda.put(id, idx);
            }
            int t = newTemp();
            // Load captured value from closure parameter
            emit("HLOAD TEMP " + t + " TEMP " + currentLambda.closureParamTemp + " " + (4 * idx));

            String type = null;
            if (currentLambda.lambdaClass != null && currentLambda.lambdaMethod != null) {
                ProgInfo.MethodInfo mi = prog_info.getMethodInfo(currentLambda.lambdaClass,
                        currentLambda.lambdaMethod);
                if (mi != null) {
                    type = mi.locals.get(id);
                    if (type == null)
                        type = mi.method_params.get(id);
                }
                if (type == null) {
                    ProgInfo.ClassInfo owner = findFieldOwner(currentLambda.lambdaClass, id);
                    if (owner != null) {
                        String hashed_var = owner.declared_class_vars_tohashed.get(id);
                        type = owner.hashed_var_types.get(hashed_var);
                    }
                }
            }
            if (type == null)
                type = "unknown";
            return new Expr("TEMP " + t, type);
        }

        if (currentClass != null &&
                prog_info.classes.containsKey(currentClass)) {
            ProgInfo.ClassInfo owner = findFieldOwner(currentClass, id);
            if (owner == null) {
                if (prog_info.classes.containsKey(id))
                    return new Expr(id, id); // class name
                return new Expr(id, "unknown");
            }
            String hashed_var = owner.declared_class_vars_tohashed.get(id);
            String type = owner.hashed_var_types.get(hashed_var);
            Integer offObj = owner.declared_var_offset.get(hashed_var);
            if (offObj == null) {
                throw new RuntimeException("Missing offset for field " + id + " in owner " + owner.class_name);
            }
            int offset = offObj;
            int t1 = newTemp();
            if (inLambda && currentLambda != null) {
                // Capture 'this' and then HLOAD field from captured 'this'
                String capName = "$this";
                Integer idx = currentLambda.indexInLambda.get(capName);
                if (idx == null) {
                    idx = currentLambda.indexInLambda.size() + 1;
                    currentLambda.indexInLambda.put(capName, idx);
                }
                int tThis = newTemp();
                emit("HLOAD TEMP " + tThis + " TEMP " + currentLambda.closureParamTemp + " " + (4 * idx));
                emit("HLOAD TEMP " + t1 + " TEMP " + tThis + " " + offset);
            } else {
                emit("HLOAD TEMP " + t1 + " TEMP 0 " + offset);
            }
            return new Expr("TEMP " + t1, type);
        } else {
            throw new RuntimeException("Unknown identifier: " + id);
        }
    }

    // search up inheritance
    private ProgInfo.ClassInfo findFieldOwner(String clazz, String field) {
        if (clazz == null)
            return null;
        ProgInfo.ClassInfo ci = prog_info.classes.get(clazz);
        while (ci != null) {
            if (ci.declared_class_vars_tohashed.containsKey(field))
                return ci;
            if (ci.class_parent == null)
                break;
            ci = prog_info.classes.get(ci.class_parent);
        }
        return null;
    }

    // ........ visitors
    /**
     * f0 -> ( ImportFunction() )?
     * f1 -> MainClass()
     * f2 -> ( TypeDeclaration() )*
     * f3 -> <EOF>
     */
    @Override
    public Expr visit(Goal n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        // print any pending lambda procedures at the very end
        for (String proc : lambdaFunctions) {
            System.out.print(proc);
        }
        return _ret;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> PrintStatement()
     * f15 -> "}"
     * f16 -> "}"
     */
    public Expr visit(MainClass n, Void argu) {
        System.out.println("MAIN");
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        currentClass = n.f1.f0.toString();
        currentMethod = "main";
        nameToTemp = new LinkedHashMap<>();
        // nameToTemp.put("this", 0);//not needed
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);
        n.f9.accept(this, argu);
        n.f10.accept(this, argu);
        String paramName = n.f11.f0.toString();
        int t = newTemp();
        nameToTemp.put(paramName, t);
        n.f12.accept(this, argu);
        n.f13.accept(this, argu);
        n.f14.accept(this, argu);
        n.f15.accept(this, argu);
        n.f16.accept(this, argu);
        currentClass = null;
        nameToTemp = null;
        currentMethod = null;
        System.out.println("END");
        return _ret;
    }

    /**
     * f0 -> ClassDeclaration()
     * | ClassExtendsDeclaration()
     */
    public Expr visit(TypeDeclaration n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    public Expr visit(ClassDeclaration n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        currentClass = n.f1.f0.toString();
        n.f2.accept(this, argu);
        // n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        currentClass = null;
        return _ret;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    public Expr visit(ClassExtendsDeclaration n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        currentClass = n.f1.f0.toString();
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        // n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        n.f7.accept(this, argu);
        currentClass = null;
        return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    @Override
    public Expr visit(VarDeclaration n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        if (currentClass != null && currentMethod != null && nameToTemp != null) {
            // local var in method
            String var_name = n.f1.f0.toString();
            int t = newTemp();
            nameToTemp.put(var_name, t);
        }
        n.f2.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    public Expr visit(MethodDeclaration n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        currentMethod = n.f2.f0.toString();
        nameToTemp = new LinkedHashMap<>();
        nameToTemp.put("this", 0); // TEMP 0 reserved for 'this'
        ProgInfo.MethodInfo mi = prog_info.getMethodInfo(currentClass, currentMethod);
        String label = mi.label;
        emit(label + " [" + (mi.param_count + 1) + "]");
        emit("BEGIN");
        // assign incoming parameters to their temps and store in nameToTemp
        List<String> paramNames = new ArrayList<>(mi.method_params.keySet());
        for (int i = 0; i < mi.param_count; ++i) {
            String paramName = paramNames.get(i);
            if (!nameToTemp.containsKey(paramName)) {
                int t = newTemp();
                nameToTemp.put(paramName, t);
            }
            int tempNum = nameToTemp.get(paramName);
            emit("MOVE TEMP " + tempNum + " TEMP " + (i + 1));
        }
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);
        n.f9.accept(this, argu);
        Expr expr = n.f10.accept(this, argu);
        n.f11.accept(this, argu);
        n.f12.accept(this, argu);
        emit("RETURN " + expr.ir);
        emit("END");
        currentMethod = null;
        nameToTemp = null;
        return _ret;

    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    public Expr visit(FormalParameterRest n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> ArrayType()
     * | BooleanType()
     * | IntegerType()
     * | Identifier()
     * | LambdaType()
     */
    public Expr visit(Type n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public Expr visit(ArrayType n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "boolean"
     */
    public Expr visit(BooleanType n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "int"
     */
    public Expr visit(IntegerType n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "Function"
     * f1 -> "<"
     * f2 -> Identifier()
     * f3 -> ","
     * f4 -> Identifier()
     * f5 -> ">"
     */
    public Expr visit(LambdaType n, Void argu) {
        // Type nodes are not emitted; handled by Collector/prog_info
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> Block()
     * | AssignmentStatement()
     * | ArrayAssignmentStatement()
     * | IfStatement()
     * | WhileStatement()
     * | PrintStatement()
     */
    public Expr visit(Statement n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    public Expr visit(Block n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public Expr visit(AssignmentStatement n, Void argu) {
        Expr _ret = null;
        String id = n.f0.f0.toString();
        n.f1.accept(this, argu);
        Expr expr_e = n.f2.accept(this, argu);
        if (expr_e == null) {
            throw new RuntimeException("Expression in assignment is null for id " + id);
        }
        String expr = expr_e.ir;
        n.f3.accept(this, argu);
        if (nameToTemp != null && nameToTemp.containsKey(id) && nameToTemp.get(id) != null) {
            int dst = nameToTemp.get(id);
            String expr_temp = makeTemp(expr);
            emit("MOVE TEMP " + dst + " " + expr_temp);
        } else {
            ProgInfo.ClassInfo owner = findFieldOwner(currentClass, id);
            if (owner == null) {
                throw new RuntimeException(
                        "Unknown assignment target (not local/param/field): " + id + " in " + currentClass);
            }
            String hashed_var = owner.declared_class_vars_tohashed.get(id);
            Integer offObj = owner.declared_var_offset.get(hashed_var);
            if (offObj == null) {
                throw new RuntimeException("No offset for inherited field " + id + " (hashed " + hashed_var + ") owner="
                        + owner.class_name);
            }
            int offset = offObj;
            String value_temp = makeTemp(expr);
            emit("HSTORE TEMP 0 " + offset + " " + value_temp);
        }
        return _ret;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    public Expr visit(ArrayAssignmentStatement n, Void argu) {
        Expr _ret = null;
        Expr arr_e = n.f0.accept(this, argu);
        String arr = arr_e.ir;
        n.f1.accept(this, argu);
        Expr index_e = n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        Expr val_e = n.f5.accept(this, argu);
        String val = val_e.ir;
        n.f6.accept(this, argu);
        String arr_temp = makeTemp(arr);
        String index_temp = makeTemp(index_e.ir);
        int len = newTemp();
        // System.out.println("haha2");
        emit("HLOAD TEMP " + len + " " + arr_temp + " 0");
        int t_lower = newTemp();
        String zeroTemp = makeTemp("0");
        emit("MOVE TEMP " + t_lower + " LE " + zeroTemp + " " + index_temp);
        int len1 = newTemp();
        emit("MOVE TEMP " + len1 + " MINUS TEMP " + len + " 1");
        int t_upper = newTemp();
        emit("MOVE TEMP " + t_upper + " LE " + index_temp + " TEMP " + len1);
        int t_ok = newTemp();
        emit("MOVE TEMP " + t_ok + " TIMES TEMP " + t_lower + " TEMP " + t_upper);
        String l_ok = newLabel("L");
        emit("CJUMP TEMP " + t_ok + " " + l_ok);
        // emit("ERROR");
        emit(l_ok);
        emit("NOOP");// safety after label
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " PLUS " + index_temp + " 1");
        int t_byte = newTemp();
        emit("MOVE TEMP " + t_byte + " TIMES TEMP " + t1 + " 4");
        int t_ptr = newTemp();
        emit("MOVE TEMP " + t_ptr + " PLUS " + arr_temp + " TEMP " + t_byte);
        String val_temp = makeTemp(val);
        emit("HSTORE TEMP " + t_ptr + " 0 " + val_temp);
        return _ret;
    }

    /**
     * f0 -> IfthenElseStatement()
     * | IfthenStatement()
     */
    public Expr visit(IfStatement n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    @Override
    public Expr visit(IfthenStatement n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr b_e = n.f2.accept(this, argu);
        String b = makeTemp(b_e.ir);
        String l1 = newLabel("L");
        emit("CJUMP " + b + " " + l1);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        emit(l1);
        emit("NOOP");// safety after label
        return _ret;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    @Override
    public Expr visit(IfthenElseStatement n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr b_e = n.f2.accept(this, argu);
        String b = makeTemp(b_e.ir);
        String l1 = newLabel("L");
        String l_end = newLabel("L");
        emit("CJUMP " + b + " " + l1);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        emit("JUMP " + l_end);
        emit(l1);
        emit("NOOP");// safety after label
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        emit(l_end);
        emit("NOOP");// safety after label
        return _ret;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    @Override
    public Expr visit(WhileStatement n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String l_loop = newLabel("L");
        String l_end = newLabel("L");
        emit(l_loop);
        emit("NOOP");// safety after label
        Expr b_e = n.f2.accept(this, argu);
        String b = makeTemp(b_e.ir);
        n.f3.accept(this, argu);
        emit("CJUMP " + b + " " + l_end);
        n.f4.accept(this, argu);
        emit("JUMP " + l_loop);
        emit(l_end);
        emit("NOOP");// safety after label
        return _ret;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    @Override
    public Expr visit(PrintStatement n, Void argu) {
        Expr _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr b_e = n.f2.accept(this, argu);
        String b = makeTemp(b_e.ir);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        emit("PRINT " + b);
        return _ret;
    }

    /**
     * f0 -> OrExpression()
     * | AndExpression()
     * | CompareExpression()
     * | neqExpression()
     * | AddExpression()
     * | MinusExpression()
     * | TimesExpression()
     * | DivExpression()
     * | ArrayLookup()
     * | ArrayLength()
     * | MessageSend()
     * | LambdaExpression()
     * | PrimaryExpression()
     */
    public Expr visit(Expression n, Void argu) {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "("
     * f1 -> Identifier()
     * f2 -> ")"
     * f3 -> "->"
     * f4 -> Expression()
     */
    public Expr visit(LambdaExpression n, Void argu) {

        String lambdaLabel = newLabel("LAMBDA");
        String paramName = n.f1.f0.toString();

        // Save outer context
        String savedCurrentMethod = currentMethod;
        LinkedHashMap<String, Integer> savedNameToTemp = nameToTemp;
        boolean savedInLambda = inLambda;
        LambdaContext savedLambda = currentLambda;

        LambdaContext lc = new LambdaContext();
        lc.label = lambdaLabel;
        lc.paramName = paramName;
        lc.lambdaClass = currentClass; // keep same class context
        lc.lambdaMethod = savedCurrentMethod;

        // push into print buffer
        printLater.push(new StringBuilder());
        inLambda = true;
        currentLambda = lc;

        // treat lambda call as a method in the current class
        currentMethod = lambdaLabel;
        nameToTemp = new LinkedHashMap<>();

        emit(lambdaLabel + " [2]");
        emit("BEGIN");

        // map incoming parameters to local temps
        int paramTemp = newTemp();
        nameToTemp.put(paramName, paramTemp);
        emit("MOVE TEMP " + paramTemp + " TEMP 0");

        int closureTemp = newTemp();
        lc.closureParamTemp = closureTemp;
        nameToTemp.put("$CLOSURE", closureTemp);
        emit("MOVE TEMP " + closureTemp + " TEMP 1");

        // Emit body and return
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        Expr body = n.f4.accept(this, argu);
        emit("RETURN " + body.ir);
        emit("END");

        String procText = printLater.pop().toString();
        lambdaFunctions.add(procText);

        // Restore outer context
        inLambda = savedInLambda;
        currentLambda = savedLambda;
        currentMethod = savedCurrentMethod;
        nameToTemp = savedNameToTemp;

        int captureCount = lc.indexInLambda.size();
        int totalBytes = 4 * (1 + captureCount); // 'this' + captured values
        int tSize = newTemp();
        emit("MOVE TEMP " + tSize + " " + totalBytes);
        int tClos = newTemp();
        emit("MOVE TEMP " + tClos + " HALLOCATE TEMP " + tSize);
        int tLbl = newTemp();
        emit("MOVE TEMP " + tLbl + " " + lambdaLabel);
        emit("HSTORE TEMP " + tClos + " 0 TEMP " + tLbl);

        // store captured values in order of their assigned index
        for (Map.Entry<String, Integer> e : lc.indexInLambda.entrySet()) {
            String capName = e.getKey();
            int idx = e.getValue();
            String srcTemp;
            if ("$this".equals(capName)) {
                // capture 'this' from the defining context (TEMP 0)
                srcTemp = makeTemp("TEMP 0");
            } else {
                Expr capExpr = getIdentifierExpr(capName);
                srcTemp = makeTemp(capExpr.ir);
            }
            emit("HSTORE TEMP " + tClos + " " + (4 * idx) + " " + srcTemp);
        }

        // Return the closure pointer as the value of the lambda expression
        return new Expr("TEMP " + tClos, "Function");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "&&"
     * f2 -> PrimaryExpression()
     */
    @Override
    public Expr visit(AndExpression n, Void argu) {
        Expr left = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr right = n.f2.accept(this, argu);
        String left_temp = makeTemp(left.ir);
        int result = newTemp();
        String l_false = newLabel("L");
        String l_end = newLabel("L");
        emit("MOVE TEMP " + result + " " + left_temp);
        emit("CJUMP TEMP " + result + " " + l_false);
        String right_temp = makeTemp(right.ir);
        emit("MOVE TEMP " + result + " " + right_temp);
        emit("JUMP " + l_end);
        emit(l_false);
        emit("NOOP");// safety after label
        emit("MOVE TEMP " + result + " 0");
        emit(l_end);
        emit("NOOP");// safety after label
        return new Expr("TEMP " + result, "boolean");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "||"
     * f2 -> PrimaryExpression()
     */
    @Override
    public Expr visit(OrExpression n, Void argu) {
        Expr left = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr right = n.f2.accept(this, argu);
        String left_temp = makeTemp(left.ir);
        int result = newTemp();
        String l_eval_right = newLabel("L");
        String l_end = newLabel("L");
        emit("MOVE TEMP " + result + " " + left_temp);
        emit("CJUMP TEMP " + result + " " + l_eval_right);
        emit("MOVE TEMP " + result + " 1");
        emit("JUMP " + l_end);
        emit(l_eval_right);
        emit("NOOP");// safety after label
        String right_temp = makeTemp(right.ir);
        emit("MOVE TEMP " + result + " " + right_temp);
        emit("JUMP " + l_end);
        emit(l_end);
        emit("NOOP");// safety after label
        return new Expr("TEMP " + result, "boolean");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<="
     * f2 -> PrimaryExpression()
     */
    @Override
    public Expr visit(CompareExpression n, Void argu) {
        Expr left = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr right = n.f2.accept(this, argu);
        String left_temp = makeTemp(left.ir);
        String right_temp = makeTemp(right.ir);
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " LE " + left_temp + " " + right_temp);
        return new Expr("TEMP " + t1, "boolean");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "!="
     * f2 -> PrimaryExpression()
     */
    @Override
    public Expr visit(neqExpression n, Void argu) {
        Expr left = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr right = n.f2.accept(this, argu);
        String left_temp = makeTemp(left.ir);
        String right_temp = makeTemp(right.ir);
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " NE " + left_temp + " " + right_temp);
        return new Expr("TEMP " + t1, "boolean");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    @Override
    public Expr visit(AddExpression n, Void argu) {
        Expr left = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr right = n.f2.accept(this, argu);
        String left_temp = makeTemp(left.ir);
        String right_temp = makeTemp(right.ir);
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " PLUS " + left_temp + " " + right_temp);
        return new Expr("TEMP " + t1, "int");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    @Override
    public Expr visit(MinusExpression n, Void argu) {
        Expr left = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr right = n.f2.accept(this, argu);
        String left_temp = makeTemp(left.ir);
        String right_temp = makeTemp(right.ir);
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " MINUS " + left_temp + " " + right_temp);
        return new Expr("TEMP " + t1, "int");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    @Override
    public Expr visit(TimesExpression n, Void argu) {
        Expr left = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr right = n.f2.accept(this, argu);
        String left_temp = makeTemp(left.ir);
        String right_temp = makeTemp(right.ir);
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " TIMES " + left_temp + " " + right_temp);
        return new Expr("TEMP " + t1, "int");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "/"
     * f2 -> PrimaryExpression()
     */
    @Override
    public Expr visit(DivExpression n, Void argu) {
        Expr left = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr right = n.f2.accept(this, argu);
        String left_temp = makeTemp(left.ir);
        String right_temp = makeTemp(right.ir);
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " DIV " + left_temp + " " + right_temp);
        return new Expr("TEMP " + t1, "int");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    @Override
    public Expr visit(ArrayLookup n, Void argu) {
        Expr arr_e = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        Expr index_e = n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        String arr_temp = makeTemp(arr_e.ir);
        String index_temp = makeTemp(index_e.ir);
        int len = newTemp();
        // System.out.println("haha3");
        emit("HLOAD TEMP " + len + " " + arr_temp + " 0");
        int t_lower = newTemp();
        String zeroTemp = makeTemp("0");
        emit("MOVE TEMP " + t_lower + " LE " + zeroTemp + " " + index_temp);
        // emit("MOVE TEMP " + t_lower + " LE 0 " + index_temp); wrong syntax
        int len1 = newTemp();
        emit("MOVE TEMP " + len1 + " MINUS TEMP " + len + " 1");
        int t_upper = newTemp();
        emit("MOVE TEMP " + t_upper + " LE " + index_temp + " TEMP " + len1);
        int t_ok = newTemp();
        emit("MOVE TEMP " + t_ok + " TIMES TEMP " + t_lower + " TEMP " + t_upper);
        String l_ok = newLabel("L");
        emit("CJUMP TEMP " + t_ok + " " + l_ok);
        // emit("ERROR");
        emit(l_ok);
        emit("NOOP");// safety after label
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " PLUS " + index_temp + " 1");
        int t2 = newTemp();
        emit("MOVE TEMP " + t2 + " TIMES TEMP " + t1 + " 4");
        int t3 = newTemp();
        emit("MOVE TEMP " + t3 + " PLUS " + arr_temp + " TEMP " + t2);
        int result = newTemp();
        // System.out.println("haha4");
        emit("HLOAD TEMP " + result + " TEMP " + t3 + " 0");
        return new Expr("TEMP " + result, "int");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public Expr visit(ArrayLength n, Void argu) {
        Expr arr_e = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        String arr = makeTemp(arr_e.ir);
        int t1 = newTemp();
        // System.out.println("haha6");
        emit("HLOAD TEMP " + t1 + " " + arr + " 0");
        return new Expr("TEMP " + t1, "int");
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    public Expr visit(MessageSend n, Void argu) {
        Expr obj_e = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String function_name = n.f2.f0.toString();
        n.f3.accept(this, argu);
        List<Expr> args = new ArrayList<>();
        if (n.f4.present()) {
            ExpressionList el = (ExpressionList) n.f4.node;
            Expr first = el.f0.accept(this, argu);
            args.add(first);
            for (Node node : el.f1.nodes) {
                ExpressionRest er = (ExpressionRest) node;
                Expr rest = er.f1.accept(this, argu);
                args.add(rest);
            }
        }
        n.f5.accept(this, argu);
        String obj_type = obj_e.type;
        // lambda apply
        if (obj_type != null && obj_type.startsWith("Function") && function_name.equals("apply")) {
            if (args.size() != 1) {
                throw new RuntimeException("Lambda apply expects exactly 1 argument");
            }
            String closureTemp = makeTemp(obj_e.ir);
            int methodPtrTemp = newTemp();
            emit("HLOAD TEMP " + methodPtrTemp + " " + closureTemp + " 0");
            String argTemp = makeTemp(args.get(0).ir);
            int callTemp = newTemp();
            // pass (arg, closurePtr)
            emit("MOVE TEMP " + callTemp + " CALL TEMP " + methodPtrTemp + " ( " + argTemp + " " + closureTemp + " )");
            // return type
            String retType = "unknown";
            int lt = obj_type.indexOf('<');
            int comma = obj_type.indexOf(',');
            int gt = obj_type.indexOf('>');
            if (lt >= 0 && comma > lt && gt > comma) {
                retType = obj_type.substring(comma + 1, gt).trim();
            }
            return new Expr("TEMP " + callTemp, retType);
        }

        // method dispatch via vtable
        ProgInfo.ClassInfo ci = prog_info.classes.get(obj_type);
        if (ci == null) {
            throw new RuntimeException("Unknown receiver type for method call: " + obj_type);
        }
        Integer methodIndex = ci.vtable_index.get(function_name);
        if (methodIndex == null) {
            throw new RuntimeException("Unknown method '" + function_name + "' in class " + obj_type);
        }
        int vtableOffset = methodIndex * 4;
        String obj_temp = makeTemp(obj_e.ir);
        int vtableTemp = newTemp();
        emit("HLOAD TEMP " + vtableTemp + " " + obj_temp + " 0");
        int methodPtrTemp = newTemp();
        emit("HLOAD TEMP " + methodPtrTemp + " TEMP " + vtableTemp + " " + vtableOffset);
        StringBuilder argList = new StringBuilder(obj_temp);
        for (Expr a : args) {
            String a_temp = makeTemp(a.ir);
            argList.append(" ").append(a_temp);
        }
        int callTemp = newTemp();
        emit("MOVE TEMP " + callTemp + " CALL TEMP " + methodPtrTemp + " ( " + argList + " )");
        // return type using method resolution
        String retType = "unknown";
        ProgInfo.MethodInfo mi = prog_info.getMethodInfo(obj_type, function_name);
        if (mi != null && mi.ret_type != null) {
            retType = mi.ret_type;
        } else if (ci.declared_methods.containsKey(function_name)) {
            retType = ci.declared_methods.get(function_name).ret_type;
        }
        return new Expr("TEMP " + callTemp, retType);
    }

    /**
     * f0 -> Expression()
     * f1 -> ( ExpressionRest() )*
     */
    public Expr visit(ExpressionList n, Void argu) {
        Expr first = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return first;
    }

    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    public Expr visit(ExpressionRest n, Void argu) {
        n.f0.accept(this, argu);
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> IntegerLiteral()
     * | TrueLiteral()
     * | FalseLiteral()
     * | Identifier()
     * | ThisExpression()
     * | ArrayAllocationExpression()
     * | AllocationExpression()
     * | NotExpression()
     * | BracketExpression()
     */
    @Override
    public Expr visit(PrimaryExpression n, Void argu) {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    @Override
    public Expr visit(IntegerLiteral n, Void argu) {
        n.f0.accept(this, argu);
        return new Expr(n.f0.toString(), "int");
    }

    /**
     * f0 -> "true"
     */
    @Override
    public Expr visit(TrueLiteral n, Void argu) {
        n.f0.accept(this, argu);
        return new Expr("1", "boolean");
    }

    /**
     * f0 -> "false"
     */
    @Override
    public Expr visit(FalseLiteral n, Void argu) {
        n.f0.accept(this, argu);
        return new Expr("0", "boolean");
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    @Override
    public Expr visit(Identifier n, Void argu) {
        n.f0.accept(this, argu);
        String id = n.f0.toString();
        return getIdentifierExpr(id);
    }

    /**
     * f0 -> "this"
     */
    @Override
    public Expr visit(ThisExpression n, Void argu) {
        n.f0.accept(this, argu);
        if (inLambda && currentLambda != null) {
            // 'this' used inside a lambda refers to captured 'this'
            String capName = "$this";
            Integer idx = currentLambda.indexInLambda.get(capName);
            if (idx == null) {
                idx = currentLambda.indexInLambda.size() + 1;
                currentLambda.indexInLambda.put(capName, idx);
            }
            int t = newTemp();
            emit("HLOAD TEMP " + t + " TEMP " + currentLambda.closureParamTemp + " " + (4 * idx));
            // type is the enclosing class
            String tpe = currentLambda.lambdaClass != null ? currentLambda.lambdaClass : currentClass;
            if (tpe == null)
                tpe = "unknown";
            return new Expr("TEMP " + t, tpe);
        }
        return new Expr("TEMP 0", currentClass);
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    @Override
    public Expr visit(ArrayAllocationExpression n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        Expr size_e = n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        String size = size_e.ir;
        String size_temp = makeTemp(size);
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " TIMES " + size_temp + " 4");
        int t2 = newTemp();
        emit("MOVE TEMP " + t2 + " PLUS TEMP " + t1 + " 4");
        int t3 = newTemp();
        emit("MOVE TEMP " + t3 + " HALLOCATE TEMP " + t2);
        emit("HSTORE TEMP " + t3 + " 0 " + size_temp);
        // init to 0
        int i = newTemp();
        emit("MOVE TEMP " + i + " 4");
        int max = newTemp();
        emit("MOVE TEMP " + max + " PLUS TEMP " + t1 + " 4");
        String l_loop = newLabel("L");
        String l_end = newLabel("L");
        emit(l_loop);
        emit("NOOP");// safety after label
        int t_cmp = newTemp();
        emit("MOVE TEMP " + t_cmp + " LE TEMP " + i + " TEMP " + max);
        emit("CJUMP TEMP " + t_cmp + " " + l_end);
        // emit("HSTORE TEMP " + t3 + " TEMP " + i + " 0");// wrong syntax
        // compute element address: ptr = t3 + i
        int eltPtr = newTemp();
        emit("MOVE TEMP " + eltPtr + " PLUS TEMP " + t3 + " TEMP " + i);
        // store 0 at offset 0 of that element address
        int zeroTemp = newTemp();
        emit("MOVE TEMP " + zeroTemp + " 0");// store 0 in a temp
        emit("HSTORE TEMP " + eltPtr + " 0 TEMP " + zeroTemp);
        // increment i by 4
        int inc = newTemp();
        emit("MOVE TEMP " + inc + " PLUS TEMP " + i + " 4");
        emit("MOVE TEMP " + i + " TEMP " + inc);
        emit("JUMP " + l_loop);
        emit(l_end);
        emit("NOOP");// safety after label
        return new Expr("TEMP " + t3, "int[]");
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    @Override
    public Expr visit(AllocationExpression n, Void argu) {
        n.f0.accept(this, argu);
        Expr c_e = n.f1.accept(this, argu);
        String cname = c_e.ir;
        ProgInfo.ClassInfo ci = prog_info.classes.get(cname);
        int vcount = ci.vtable.size();
        int tbytes = newTemp();
        emit("MOVE TEMP " + tbytes + " " + (4 * vcount));
        int talloc = newTemp();
        emit("MOVE TEMP " + talloc + " HALLOCATE TEMP " + tbytes);
        for (int i = 0; i < vcount; ++i) {
            String funcLabel = ci.vtable.get(i);
            int tlabel = newTemp();
            emit("MOVE TEMP " + tlabel + " " + funcLabel);
            emit("HSTORE TEMP " + talloc + " " + (4 * i) + " TEMP " + tlabel);
        }
        int numFields = ci.total_var_count;
        int obj_size = 4 * (1 + numFields);
        int t_size = newTemp();
        emit("MOVE TEMP " + t_size + " " + obj_size);
        int obj = newTemp();
        emit("MOVE TEMP " + obj + " HALLOCATE TEMP " + t_size);
        emit("HSTORE TEMP " + obj + " 0 TEMP " + talloc);
        for (String hashed : ci.final_vars_hashed) {
            int sep = hashed.indexOf('$');
            String owner = hashed.substring(0, sep);
            int offset = prog_info.classes.get(owner).declared_var_offset.get(hashed);
            int zeroTemp = newTemp();
            emit("MOVE TEMP " + zeroTemp + " 0");
            emit("HSTORE TEMP " + obj + " " + offset + " TEMP " + zeroTemp);//
        }
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        return new Expr("TEMP " + obj, cname);
    }

    /**
     * f0 -> "!"
     * f1 -> Expression()
     */
    @Override
    public Expr visit(NotExpression n, Void argu) {
        n.f0.accept(this, argu);
        Expr b_e = n.f1.accept(this, argu);
        String b = makeTemp(b_e.ir);
        int t1 = newTemp();
        emit("MOVE TEMP " + t1 + " MINUS 1 " + b);
        return new Expr("TEMP " + t1, "boolean");
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    @Override
    public Expr visit(BracketExpression n, Void argu) {
        n.f0.accept(this, argu);
        Expr _ret = n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return _ret;
    }

}
