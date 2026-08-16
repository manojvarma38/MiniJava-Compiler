package visitor;

import syntaxtree.*;
import java.util.*;

public class TypeChecker extends GJDepthFirst<String, Void> {
    private String cur_class = null;
    private String cur_method = null;
    private ProgInfo prog_info = null;
    private boolean imp_flag = false;
    Map<String, String> local_vars = new HashMap<>();

    public TypeChecker(ProgInfo prog_info) {
        this.prog_info = prog_info;
    }

    /* ---------------- helpers ---------------- */
    // extrack lambda from nested bracket expressions
    private LambdaExpression unwrapLambda(Expression expr) {
        if (expr == null)
            return null;

        Node choice = expr.f0.choice;

        // direct lambda
        if (choice instanceof LambdaExpression)
            return (LambdaExpression) choice;

        // parentheses: ( Expression )
        // (f1)
        if (choice instanceof BracketExpression) {
            BracketExpression be = (BracketExpression) choice;
            return unwrapLambda(be.f1);
        }

        // lambda nested under a PrimaryExpression
        if (choice instanceof PrimaryExpression) {
            PrimaryExpression pe = (PrimaryExpression) choice;
            Node pe_choice = pe.f0.choice;
            if (pe_choice instanceof LambdaExpression)
                return (LambdaExpression) pe_choice;
            if (pe_choice instanceof BracketExpression) {
                BracketExpression be = (BracketExpression) pe_choice;
                return unwrapLambda(be.f1);
            }
        }

        // no lambda found
        return null;
    }

    private boolean isFunctionType(String s) {
        return s != null && s.startsWith("Function");
    }

    // check parent classes
    private boolean superTypeChecker(String lhs, String rhs) {
        if (lhs == null || rhs == null)
            return false;
        if (lhs.equals(rhs))
            return true;
        String t = rhs;
        if (!prog_info.classes.containsKey(t))
            return lhs.equals(rhs);
        while (prog_info.classes.get(t).class_parent != null) {
            t = prog_info.classes.get(t).class_parent;
            if (t.equals(lhs))
                return true;
        }
        return false;
    }

    private String getVarType(String name) {
        if (name == null)
            return null;

        // local vars
        if (local_vars.containsKey(name))
            return local_vars.get(name);

        // method params
        if (cur_class != null && cur_method != null) {
            ProgInfo.ClassInfo cinfo = prog_info.classes.get(cur_class);
            if (cinfo != null) {
                ProgInfo.MethodInfo mi = cinfo.methods.get(cur_method);
                if (mi != null && mi.method_params.containsKey(name))
                    return mi.method_params.get(name);
            }
        }

        // class fields, search up parents
        String c_cl = cur_class;
        while (c_cl != null) {
            ProgInfo.ClassInfo ci = prog_info.classes.get(c_cl);
            if (ci != null && ci.class_vars.containsKey(name))
                return ci.class_vars.get(name);
            if (ci == null)
                break;
            c_cl = ci.class_parent;
        }

        return null;
    }

    private ProgInfo.MethodInfo findMethodInfo(String class_name, String method_name) {
        // search up parents
        String c_cl = class_name;
        while (c_cl != null) {
            ProgInfo.ClassInfo ci = prog_info.classes.get(c_cl);
            if (ci == null)
                break;
            if (ci.methods.containsKey(method_name))
                return ci.methods.get(method_name);
            c_cl = ci.class_parent;
        }
        return null;
    }

    // parse Function<from,to> into a String[2] {from,to}
    private String[] getFunctionTypes(String func) {
        if (func == null || !func.startsWith("Function"))
            return null;
        int lt = func.indexOf('<');
        int comma = func.indexOf(',');
        int gt = func.lastIndexOf('>');
        if (lt < 0 || comma < 0 || gt < 0)
            return null;
        String from = func.substring(lt + 1, comma).trim();
        String to = func.substring(comma + 1, gt).trim();
        return new String[] { from, to };
    }

    // binds lambda parameter name to from type in local_vars
    private String BindLambda(LambdaExpression le, String expectedFuncType, Void argu) {
        if (expectedFuncType == null || !isFunctionType(expectedFuncType))
            throw new MyException.TypeError();

        String[] parts = getFunctionTypes(expectedFuncType);
        if (parts == null)
            throw new MyException.TypeError();
        String fromType = parts[0];
        String toType = parts[1];

        // parameter name
        String paramName = le.f1.f0.toString();

        // save previous binding if any, then bind
        boolean hadPrev = local_vars.containsKey(paramName);
        String prev = local_vars.get(paramName);
        local_vars.put(paramName, fromType);

        // type-check body
        String bodyType = le.f4.accept(this, argu);

        // restore previous binding
        if (hadPrev)
            local_vars.put(paramName, prev);
        else
            local_vars.remove(paramName);

        if (bodyType == null)
            throw new MyException.SymbolNotFound();

        if (!superTypeChecker(toType, bodyType))
            throw new MyException.TypeError();

        return expectedFuncType;
    }

    /* ---------------- visitors ---------------- */

    @Override
    public String visit(Goal n, Void argu) {
        if (n.f0.present()) {
            imp_flag = true;
            n.f0.accept(this, argu);
        }
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        return null;
    }

    public String visit(ImportFunction n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return "null";
    }

    @Override
    public String visit(MainClass n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        if (!prog_info.classes.containsKey(n.f1.f0.toString()))
            throw new MyException.SymbolNotFound();
        cur_class = n.f1.f0.toString();
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        cur_method = "main";
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);
        n.f9.accept(this, argu);
        n.f10.accept(this, argu);
        n.f11.accept(this, argu);
        n.f12.accept(this, argu);
        n.f13.accept(this, argu);
        n.f14.accept(this, argu);
        n.f15.accept(this, argu);
        n.f16.accept(this, argu);
        cur_class = null;
        cur_method = null;
        local_vars.clear();
        return null;
    }

    public String visit(TypeDeclaration n, Void argu) {
        n.f0.accept(this, argu);
        return null;
    }

    @Override
    public String visit(ClassDeclaration n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        if (!prog_info.classes.containsKey(n.f1.f0.toString()))
            throw new MyException.SymbolNotFound();
        cur_class = n.f1.f0.toString();
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        cur_class = null;
        return null;
    }

    @Override
    public String visit(ClassExtendsDeclaration n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        if (!prog_info.classes.containsKey(n.f1.f0.toString()))
            throw new MyException.SymbolNotFound();
        if (!prog_info.classes.containsKey(n.f3.f0.toString()))
            throw new MyException.SymbolNotFound();
        cur_class = n.f1.f0.toString();
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        n.f7.accept(this, argu);
        cur_class = null;
        return null;
    }

    @Override
    public String visit(VarDeclaration n, Void argu) {
        String type = n.f0.accept(this, argu);
        String identifier = n.f1.f0.toString();
        if (cur_class != null && cur_method != null) {
            if (prog_info.classes.get(cur_class).methods.get(cur_method).method_params.containsKey(identifier))
                throw new MyException.TypeError();
            if (local_vars.containsKey(identifier))
                throw new MyException.TypeError();
            local_vars.put(identifier, type);
        }
        n.f2.accept(this, argu);
        return null;
    }

    @Override
    public String visit(MethodDeclaration n, Void argu) {
        n.f0.accept(this, argu);
        String func_ret_type = n.f1.accept(this, argu);
        cur_method = n.f2.f0.toString();

        // clear locals
        local_vars.clear();

        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        n.f7.accept(this, argu); // var declarations populate local_vars
        n.f8.accept(this, argu); // statements
        n.f9.accept(this, argu);

        // lambda return case
        LambdaExpression retLambda = unwrapLambda(n.f10);
        String expr_type;
        if (retLambda != null && isFunctionType(func_ret_type)) {
            expr_type = BindLambda(retLambda, func_ret_type, argu);
        } else {
            expr_type = n.f10.accept(this, argu);
        }

        if (func_ret_type == null || expr_type == null)
            throw new MyException.SymbolNotFound();

        // lambda type check
        if (isFunctionType(expr_type)) {
            if (!isFunctionType(func_ret_type))
                throw new MyException.TypeError();
            int func_comma_index = func_ret_type.indexOf(',');
            int expr_comma_index = expr_type.indexOf(',');
            String func_id1 = func_ret_type.substring(9, func_comma_index);
            String func_id2 = func_ret_type.substring(func_comma_index + 1, func_ret_type.length() - 1);
            String expr_id1 = expr_type.substring(9, expr_comma_index);
            String expr_id2 = expr_type.substring(expr_comma_index + 1, expr_type.length() - 1);
            if (!superTypeChecker(func_id1, expr_id1) || !superTypeChecker(func_id2, expr_id2))
                throw new MyException.TypeError();
        } else if (!superTypeChecker(func_ret_type, expr_type)) {
            throw new MyException.TypeError();
        }

        n.f11.accept(this, argu);
        n.f12.accept(this, argu);

        cur_method = null;
        local_vars.clear();
        return null;
    }

    public String visit(FormalParameterList n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return null;
    }

    public String visit(FormalParameter n, Void argu) {
        String t = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return t;
    }

    public String visit(FormalParameterRest n, Void argu) {
        n.f0.accept(this, argu);
        return n.f1.accept(this, argu);
    }

    public String visit(Type n, Void argu) {
        return n.f0.accept(this, argu);
    }

    public String visit(ArrayType n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return "int[]";
    }

    public String visit(BooleanType n, Void argu) {
        n.f0.accept(this, argu);
        return "boolean";
    }

    public String visit(IntegerType n, Void argu) {
        n.f0.accept(this, argu);
        return "int";
    }

    @Override
    public String visit(LambdaType n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String from = n.f2.accept(this, argu);
        if ("Integer".equals(from))// different representation
            from = "int";
        if ("Boolean".equals(from))
            from = "boolean";
        if (!("int".equals(from) || "boolean".equals(from) || "int[]".equals(from)
                || prog_info.classes.containsKey(from))) {
            throw new MyException.SymbolNotFound();
        }
        n.f3.accept(this, argu);
        String to = n.f4.accept(this, argu);
        if ("Integer".equals(to))
            to = "int";
        if ("Boolean".equals(to))
            to = "boolean";
        if (!("int".equals(to) || "boolean".equals(to) || "int[]".equals(to) || prog_info.classes.containsKey(to))) {
            throw new MyException.SymbolNotFound();
        }
        n.f5.accept(this, argu);
        if (!imp_flag)
            throw new MyException.SymbolNotFound();
        return "Function<" + from + "," + to + ">";
    }

    public String visit(Statement n, Void argu) {
        return n.f0.accept(this, argu);
    }

    public String visit(Block n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return null;
    }

    @Override
    public String visit(AssignmentStatement n, Void argu) {
        String i_name = n.f0.f0.toString();
        String i_type = getVarType(i_name);
        if (i_type == null)
            throw new MyException.SymbolNotFound();
        n.f1.accept(this, argu);

        // rhs is lambda
        LambdaExpression rhsLambda = unwrapLambda(n.f2);
        if (rhsLambda != null && isFunctionType(i_type)) {
            String e_type = BindLambda(rhsLambda, i_type, argu);
            // e_type equals i_type on success
        } else {
            String e_type = n.f2.accept(this, argu);
            if (e_type == null)
                throw new MyException.SymbolNotFound();

            if (isFunctionType(e_type)) {
                if (!isFunctionType(i_type))
                    throw new MyException.TypeError();
                int func_comma_index = i_type.indexOf(',');
                int expr_comma_index = e_type.indexOf(',');
                String func_id1 = i_type.substring(9, func_comma_index);
                String func_id2 = i_type.substring(func_comma_index + 1, i_type.length() - 1);
                String expr_id1 = e_type.substring(9, expr_comma_index);
                String expr_id2 = e_type.substring(expr_comma_index + 1, e_type.length() - 1);
                if (!superTypeChecker(func_id1, expr_id1) || !superTypeChecker(func_id2, expr_id2))
                    throw new MyException.TypeError();
            } else if (!superTypeChecker(i_type, e_type)) {
                throw new MyException.TypeError();
            }
        }

        n.f3.accept(this, argu);
        return null;
    }

    @Override
    public String visit(ArrayAssignmentStatement n, Void argu) {
        // n.f0 is Identifier -> resolve variable type explicitly
        String varName = n.f0.f0.toString();
        String t1 = getVarType(varName);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"int[]".equals(t1))
            throw new MyException.TypeError();

        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t2 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t2))
            throw new MyException.TypeError();
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        String t3 = n.f5.accept(this, argu);
        if (t3 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t3))
            throw new MyException.TypeError();
        n.f6.accept(this, argu);
        return null;
    }

    @Override
    public String visit(IfthenStatement n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String t1 = n.f2.accept(this, argu);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"boolean".equals(t1))
            throw new MyException.TypeError();
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return "IfthenStatement";
    }

    @Override
    public String visit(IfthenElseStatement n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String t1 = n.f2.accept(this, argu);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"boolean".equals(t1))
            throw new MyException.TypeError();
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        return "IfthenElseStatement";
    }

    @Override
    public String visit(WhileStatement n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String t1 = n.f2.accept(this, argu);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"boolean".equals(t1))
            throw new MyException.TypeError();
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return "WhileStatement";
    }

    @Override
    public String visit(PrintStatement n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String t1 = n.f2.accept(this, argu);
        if (t1 == null)
            throw new MyException.TypeError();
        if (!"int".equals(t1))
            throw new MyException.TypeError();
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return null;
    }

    @Override
    public String visit(Expression n, Void argu) {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(LambdaExpression n, Void argu) {
        // stale visit,error if it reaches here
        throw new MyException.TypeError();
    }

    @Override
    public String visit(AndExpression n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t1 == null || t2 == null)
            throw new MyException.SymbolNotFound();
        if (!("boolean".equals(t1) && "boolean".equals(t2)))
            throw new MyException.TypeError();
        return "boolean";
    }

    @Override
    public String visit(OrExpression n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t1 == null || t2 == null)
            throw new MyException.SymbolNotFound();
        if (!("boolean".equals(t1) && "boolean".equals(t2)))
            throw new MyException.TypeError();
        return "boolean";
    }

    @Override
    public String visit(CompareExpression n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t1 == null || t2 == null)
            throw new MyException.SymbolNotFound();
        if (!("int".equals(t1) && "int".equals(t2)))
            throw new MyException.TypeError();
        return "boolean";
    }

    @Override
    public String visit(neqExpression n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t1 == null || t2 == null)
            throw new MyException.SymbolNotFound();
        if (!(("int".equals(t1) && "int".equals(t2)) || ("boolean".equals(t1) && "boolean".equals(t2)))) {
            throw new MyException.TypeError();
        }
        return "boolean";
    }

    @Override
    public String visit(AddExpression n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t1))
            throw new MyException.TypeError();
        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t2 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t2))
            throw new MyException.TypeError();
        return "int";
    }

    @Override
    public String visit(MinusExpression n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t1))
            throw new MyException.TypeError();
        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t2 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t2))
            throw new MyException.TypeError();
        return "int";
    }

    @Override
    public String visit(TimesExpression n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t1))
            throw new MyException.TypeError();
        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t2 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t2))
            throw new MyException.TypeError();
        return "int";
    }

    @Override
    public String visit(DivExpression n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t1))
            throw new MyException.TypeError();
        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t2 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t2))
            throw new MyException.TypeError();
        return "int";
    }

    @Override
    public String visit(ArrayLookup n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"int[]".equals(t1))
            throw new MyException.TypeError();
        n.f1.accept(this, argu);
        String t2 = n.f2.accept(this, argu);
        if (t2 == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(t2))
            throw new MyException.TypeError();
        n.f3.accept(this, argu);
        return "int";
    }

    @Override
    public String visit(ArrayLength n, Void argu) {
        String t1 = n.f0.accept(this, argu);
        if (t1 == null)
            throw new MyException.SymbolNotFound();
        if (!"int[]".equals(t1))
            throw new MyException.TypeError();
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return "int";
    }

    public String visit(MessageSend n, Void argu) {
        try {
            String recvType = n.f0.accept(this, argu); // primary expression
            n.f1.accept(this, argu); // "."
            String rawMethod = n.f2.f0.toString();
            String methodName = n.f2.accept(this, argu); // token text
            n.f3.accept(this, argu); // "("

            if (recvType == "int" || recvType == "boolean") {
                throw new MyException.TypeError();
            }
            if (recvType == "int[]") {
                throw new MyException.SymbolNotFound();
            }
            // resolve "this"
            if ("this".equals(recvType)) {
                if (cur_class == null)
                    throw new MyException.TypeError();
                recvType = cur_class;
            }

            // Function<from,to>.apply()
            if (recvType != null && recvType.startsWith("Function")) {
                // only allow apply
                if (!"apply".equals(rawMethod))
                    throw new MyException.SymbolNotFound();

                // parse function types
                String[] parts = getFunctionTypes(recvType);
                if (parts == null)
                    throw new MyException.TypeError();
                String fromType = parts[0];
                String toType = parts[1];

                // collect arguments: must be exactly ONE argument
                List<String> argTypes = new ArrayList<>();
                if (n.f4.present()) {
                    ExpressionList el = (ExpressionList) n.f4.node;
                    // build list of Expression nodes
                    List<Expression> exprNodes = new ArrayList<>();
                    exprNodes.add(el.f0);
                    for (Object o : el.f1.nodes) {
                        ExpressionRest er = (ExpressionRest) o;
                        exprNodes.add(er.f1);
                    }
                    if (exprNodes.size() != 1)
                        throw new MyException.TypeError();

                    // the single argument may itself be a lambda or an expression
                    Expression argExpr = exprNodes.get(0);
                    LambdaExpression le = unwrapLambda(argExpr);
                    if (le != null) {
                        // treat as lambda: check against expected function type
                        argTypes.add(BindLambda(le, fromType, argu));
                    } else {
                        // normal expression: evaluate it and ensure it matches fromType
                        String at = argExpr.accept(this, argu);
                        if (at == null)
                            throw new MyException.SymbolNotFound();
                        argTypes.add(at);
                    }
                } else {
                    // no arguments provided
                    throw new MyException.TypeError();
                }
                n.f5.accept(this, argu);

                String a = argTypes.get(0);
                if (a == null)
                    throw new MyException.SymbolNotFound();
                if (!superTypeChecker(fromType, a))
                    throw new MyException.TypeError();

                // result type is 'to' type
                return toType;
            }

            // find method
            ProgInfo.MethodInfo mi = findMethodInfo(recvType, methodName);
            if (mi == null) {
                if (rawMethod != null && !rawMethod.equals(methodName)) {
                    mi = findMethodInfo(recvType, rawMethod);
                    if (mi != null)
                        methodName = rawMethod;
                }
            }
            if (mi == null)
                throw new MyException.TypeError();

            // collect param types (declared order)
            List<String> paramTypes = new ArrayList<>(mi.method_params.values());

            // evaluate arguments
            List<String> argTypes = new ArrayList<>();
            if (n.f4.present()) {
                ExpressionList el = (ExpressionList) n.f4.node;
                List<Expression> exprNodes = new ArrayList<>();
                exprNodes.add(el.f0);
                for (Object o : el.f1.nodes) {
                    ExpressionRest er = (ExpressionRest) o;
                    exprNodes.add(er.f1);
                }

                if (exprNodes.size() != paramTypes.size())
                    throw new MyException.TypeError();

                for (int i = 0; i < exprNodes.size(); ++i) {
                    Expression expr = exprNodes.get(i);
                    String expectedParamType = paramTypes.get(i);
                    LambdaExpression le = unwrapLambda(expr);
                    if (le != null) {
                        // lambda present — expected param must be a Function<>
                        if (!isFunctionType(expectedParamType))
                            throw new MyException.TypeError();
                        argTypes.add(BindLambda(le, expectedParamType, argu));
                    } else {
                        String at = expr.accept(this, argu);
                        if (at == null)
                            throw new MyException.SymbolNotFound();
                        argTypes.add(at);
                    }
                }
            } else {
                if (!paramTypes.isEmpty())
                    throw new MyException.TypeError();
            }

            n.f5.accept(this, argu);

            // check params vs args
            for (int i = 0; i < paramTypes.size(); ++i) {
                String p = paramTypes.get(i);
                String a = argTypes.get(i);
                if (a == null)
                    throw new MyException.SymbolNotFound();

                if (isFunctionType(a)) {
                    if (!isFunctionType(p))
                        throw new MyException.TypeError();
                    int func_comma_index = p.indexOf(',');
                    int expr_comma_index = a.indexOf(',');
                    String func_id1 = p.substring(9, func_comma_index);
                    String func_id2 = p.substring(func_comma_index + 1, p.length() - 1);
                    String expr_id1 = a.substring(9, expr_comma_index);
                    String expr_id2 = a.substring(expr_comma_index + 1, a.length() - 1);
                    if (!superTypeChecker(func_id1, expr_id1) || !superTypeChecker(func_id2, expr_id2)) {
                        throw new MyException.TypeError();
                    }
                } else if (!superTypeChecker(p, a)) {
                    throw new MyException.TypeError();
                }
            }

            return mi.ret_type;
        } catch (MyException.SymbolNotFound e) {
            throw e;
        } catch (Exception ex) {
            throw new MyException.TypeError();
        }
    }

    public String visit(ExpressionList n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return null;
    }

    public String visit(ExpressionRest n, Void argu) {
        n.f0.accept(this, argu);
        return n.f1.accept(this, argu);
    }

    @Override
    public String visit(PrimaryExpression n, Void argu) {
        // If the primary is an Identifier used as expression, resolve variable
        Node choice = n.f0.choice;
        if (choice instanceof Identifier) {
            String name = ((Identifier) choice).f0.toString();
            String varType = getVarType(name);
            if (varType != null)
                return varType;
            // identifier used as expression but not found
            throw new MyException.SymbolNotFound();
        }
        // otherwise delegate
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(IntegerLiteral n, Void argu) {
        n.f0.accept(this, argu);
        return "int";
    }

    @Override
    public String visit(TrueLiteral n, Void argu) {
        n.f0.accept(this, argu);
        return "boolean";
    }

    @Override
    public String visit(FalseLiteral n, Void argu) {
        n.f0.accept(this, argu);
        return "boolean";
    }

    @Override
    public String visit(Identifier n, Void argu) {
        // Return raw token text
        return n.f0.toString();
    }

    @Override
    public String visit(ThisExpression n, Void argu) {
        n.f0.accept(this, argu);
        if (cur_class == null)
            throw new MyException.TypeError();
        return cur_class;
    }

    @Override
    public String visit(ArrayAllocationExpression n, Void argu) {
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        String exprtype = n.f3.accept(this, argu);
        if (exprtype == null)
            throw new MyException.SymbolNotFound();
        if (!"int".equals(exprtype))
            throw new MyException.TypeError();
        n.f4.accept(this, argu);
        return "int[]";
    }

    @Override
    public String visit(AllocationExpression n, Void argu) {
        n.f0.accept(this, argu);
        String t1 = n.f1.accept(this, argu); // identifier token text
        if (t1 == null || !prog_info.classes.containsKey(t1)) {
            String raw = n.f1.f0.toString();
            if (raw == null || !prog_info.classes.containsKey(raw)) {
                throw new MyException.SymbolNotFound(); // unknown class
            } else {
                t1 = raw;
            }
        }
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        return t1;
    }

    @Override
    public String visit(NotExpression n, Void argu) {
        n.f0.accept(this, argu);
        String exprtype = n.f1.accept(this, argu);
        if (exprtype == null)
            throw new MyException.SymbolNotFound();
        if (!"boolean".equals(exprtype))
            throw new MyException.TypeError();
        return "boolean";
    }

    @Override
    public String visit(BracketExpression n, Void argu) {
        n.f0.accept(this, argu);
        String exprtype = n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return exprtype;
    }
}
