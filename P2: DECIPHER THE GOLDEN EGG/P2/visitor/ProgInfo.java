package visitor;

import java.util.*;

public class ProgInfo {
    static class ClassInfo {
        public String class_name;
        public String class_parent;
        public Map<String, String> class_vars = new HashMap<>();// name->type
        public Map<String, MethodInfo> methods = new HashMap<>();// func_name->func_info

        public ClassInfo(String c_name, String c_parent) {
            class_name = c_name;
            class_parent = c_parent;
        }
    }

    static class MethodInfo {
        public String method_name;
        public String ret_type;
        public Map<String, String> method_params = new LinkedHashMap<>();// name->type
    }

    public Map<String, ClassInfo> classes = new HashMap<>();// class_name -> classinfo

    public void addClass(String c_name, String c_parent) {
        if (classes.containsKey(c_name))
            throw new MyException.TypeError();// re-declaration ,throw exception
        classes.put(c_name, new ClassInfo(c_name, c_parent));
    }

    public void addClassVar(String c_name, String var_name, String var_type) {
        ClassInfo cur_class = classes.get(c_name);
        if (cur_class == null)
            throw new MyException.SymbolNotFound();
        if (cur_class.class_vars.containsKey(var_name))
            throw new MyException.TypeError();
        cur_class.class_vars.put(var_name, var_type);
    }

    public void addMethod(String class_name, String method_name, String ret_type) {
        ClassInfo cur_class = classes.get(class_name);
        if (cur_class == null)
            throw new MyException.SymbolNotFound();
        if (cur_class.methods.containsKey(method_name))
            throw new MyException.TypeError();
        MethodInfo method_info = new MethodInfo();
        method_info.method_name = method_name;
        method_info.ret_type = ret_type;
        cur_class.methods.put(method_name, method_info);
    }

    public MethodInfo getMethodInfo(String class_name, String method_name) {
        ClassInfo cur_class = classes.get(class_name);
        if (cur_class == null)
            throw new MyException.SymbolNotFound();
        if (!cur_class.methods.containsKey(method_name))
            throw new MyException.TypeError();
        return cur_class.methods.get(method_name);
    }

    public void addMethodParam(String class_name, String method_name, String var_name, String var_type) {
        MethodInfo cur_MethodInfo = getMethodInfo(class_name, method_name);
        if (cur_MethodInfo.method_params.containsKey(var_name))
            throw new MyException.TypeError();
        cur_MethodInfo.method_params.put(var_name, var_type);
    }

    public void printInfo() {
        for (var classEntry : classes.entrySet()) {
            ClassInfo cinfo = classEntry.getValue();
            System.out.println("Class: " + cinfo.class_name +
                    (cinfo.class_parent != null ? " extends " + cinfo.class_parent : ""));

            if (!cinfo.class_vars.isEmpty()) {
                System.out.println("  Class Variables:");
                for (var varEntry : cinfo.class_vars.entrySet()) {
                    System.out.println("    " + varEntry.getKey() + " : " + varEntry.getValue());
                }
            }

            if (!cinfo.methods.isEmpty()) {
                System.out.println("  Methods:");
                for (var methodEntry : cinfo.methods.entrySet()) {
                    MethodInfo minfo = methodEntry.getValue();
                    System.out.print("    " + minfo.method_name + "(");
                    boolean first = true;
                    for (var paramEntry : minfo.method_params.entrySet()) {
                        if (!first)
                            System.out.print(", ");
                        System.out.print(paramEntry.getKey() + " : " + paramEntry.getValue());
                        first = false;
                    }
                    System.out.println(") : " + minfo.ret_type);
                }
            }
            System.out.println();
        }
    }

}
