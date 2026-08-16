package visitor;

import syntaxtree.*;

public class Collector extends GJDepthFirst<Void, Void> {

    private String cur_class = null;
    private String cur_method = null;
    private ProgInfo prog_info = null;

    public Collector(ProgInfo prog_info) {
        this.prog_info = prog_info;
    }

    public String getType(Type n) {
        Node choice = n.f0.choice;
        if (choice instanceof ArrayType) {
            return "int[]";
        } else if (choice instanceof BooleanType) {
            return "boolean";
        } else if (choice instanceof IntegerType) {
            return "int";
        } else if (choice instanceof Identifier) {
            return ((Identifier) choice).f0.toString();
        } else if (choice instanceof LambdaType) {
            LambdaType lt = (LambdaType) choice;
            String from = lt.f2.f0.toString();
            String to = lt.f4.f0.toString();
            return "Function<" + from + "," + to + ">";
        } else {
            return "unknown";
        }
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "Void"
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
    @Override
    public Void visit(MainClass n, Void argu) {
        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String class_name = n.f1.f0.toString();
        prog_info.addClass(class_name, null);
        cur_class = class_name;
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        prog_info.addMethod(class_name, "main", "void");
        cur_method = "main";
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);
        n.f9.accept(this, argu);
        n.f10.accept(this, argu);
        n.f11.accept(this, argu);
        String arg_name = n.f11.f0.toString();
        prog_info.addMethodParam(class_name, "main", arg_name, "String[]");
        n.f12.accept(this, argu);
        n.f13.accept(this, argu);
        n.f14.accept(this, argu);
        n.f15.accept(this, argu);
        n.f16.accept(this, argu);

        cur_class = null;
        cur_method = null;
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
    @Override
    public Void visit(ClassDeclaration n, Void argu) {

        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        String class_name = n.f1.f0.toString();
        prog_info.addClass(class_name, null);
        cur_class = class_name;
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        cur_class = null;
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
    @Override
    public Void visit(ClassExtendsDeclaration n, Void argu) {

        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        String class_name = n.f1.f0.toString();
        String parent_name = n.f3.f0.toString();
        prog_info.addClass(class_name, parent_name);
        cur_class = class_name;
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        n.f7.accept(this, argu);
        cur_class = null;
        return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    @Override
    public Void visit(VarDeclaration n, Void argu) {

        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        if (cur_class != null && cur_method == null) {
            String identifier = n.f1.f0.toString();
            if (prog_info.classes.get(cur_class).class_vars.containsKey(identifier)) {
                throw new MyException.TypeError();
            }
            String type = getType(n.f0);
            prog_info.addClassVar(cur_class, n.f1.f0.toString(), type);
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
    @Override
    public Void visit(MethodDeclaration n, Void argu) {

        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        String type = getType(n.f1);
        String method_name = n.f2.f0.toString();
        cur_method = method_name;
        prog_info.addMethod(cur_class, method_name, type);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);
        n.f6.accept(this, argu);
        n.f7.accept(this, argu);
        n.f8.accept(this, argu);
        n.f9.accept(this, argu);
        n.f10.accept(this, argu);
        n.f11.accept(this, argu);
        n.f12.accept(this, argu);
        cur_method = null;
        return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public Void visit(FormalParameter n, Void argu) {

        Void _ret = null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        if (cur_class != null && cur_method != null) {
            String type = getType(n.f0);
            String var_name = n.f1.f0.toString();
            prog_info.addMethodParam(cur_class, cur_method, var_name, type);
        }
        return _ret;
    }
}
