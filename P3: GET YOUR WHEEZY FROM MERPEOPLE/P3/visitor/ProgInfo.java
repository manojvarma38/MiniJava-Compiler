package visitor;

import java.util.*;
import syntaxtree.*;
import visitor.ProgInfo.ClassInfo;
import visitor.ProgInfo.MethodInfo;

public class ProgInfo {
    public static class ClassInfo {
        public String class_name;
        public String class_parent;
        public List<String> declared_class_vars = new ArrayList<>();// name
        public LinkedHashMap<String, String> hashed_var_types = new LinkedHashMap<>();// name->type
        public LinkedHashMap<String, MethodInfo> declared_methods = new LinkedHashMap<>();
        public LinkedHashMap<String, String> declared_class_vars_tohashed = new LinkedHashMap<>();// original to hashed
                                                                                                  // var

        // after accumulating inherited fields and methods
        public int total_var_count = 0;
        // removing this coz of shadowing issue
        // public LinkedHashMap<String, Integer> final_var_offset = new
        // LinkedHashMap<>(); // name -> byte offset
        public LinkedHashMap<String, Integer> declared_var_offset = new LinkedHashMap<>();
        //
        public List<String> final_vars_hashed = new ArrayList<>(); // all fields including inherited

        public List<String> vtable = new ArrayList<>(); // labels
        public LinkedHashMap<String, Integer> vtable_index = new LinkedHashMap<>(); // methodName -> index

        public boolean vis = false;

        public ClassInfo(String c_name, String c_parent) {
            class_name = c_name;
            class_parent = c_parent;
        }
    }

    public String HashVar(String c_name, String var_name) {
        return c_name + "$" + var_name;
    }

    public static class MethodInfo {
        public String method_name;
        public String ret_type;
        public LinkedHashMap<String, String> method_params = new LinkedHashMap<>();// name->type
        public LinkedHashMap<String, String> locals = new LinkedHashMap<>(); // local name -> type
        public MethodDeclaration astNode = null;

        // after accumulating
        public String label = null;
        public int index = -1;
        public int param_count = 0; // number of declared params (not counting this)
    }

    public LinkedHashMap<String, ClassInfo> classes = new LinkedHashMap<>();// class_name -> classinfo

    public void addClass(String c_name, String c_parent) {
        classes.put(c_name, new ClassInfo(c_name, c_parent));
    }

    public void addClassVar(String c_name, String var_name, String var_type) {
        // hash the var to handle shadowing
        ClassInfo cur_class = classes.get(c_name);
        String hashed_var = HashVar(c_name, var_name);
        cur_class.declared_class_vars.add(var_name);
        cur_class.declared_class_vars_tohashed.put(var_name, hashed_var);
        cur_class.hashed_var_types.put(hashed_var, var_type);
    }

    public void addMethod(String class_name, String method_name, String ret_type, MethodDeclaration astNode) {
        ClassInfo cur_class = classes.get(class_name);
        MethodInfo method_info = new MethodInfo();
        method_info.method_name = method_name;
        method_info.ret_type = ret_type;
        method_info.astNode = astNode;
        cur_class.declared_methods.put(method_name, method_info);
    }

    public MethodInfo getMethodInfo(String class_name, String method_name) {
        ClassInfo cur_class = classes.get(class_name);
        return cur_class.declared_methods.get(method_name);
    }

    public void addMethodParam(String class_name, String method_name, String var_name, String var_type) {
        // System.out.println(
        // "addMethodParam called: class=" + class_name + " method=" + method_name + "
        // param=" + var_name);
        MethodInfo cur_MethodInfo = getMethodInfo(class_name, method_name);
        cur_MethodInfo.method_params.put(var_name, var_type);
    }

    public void accumulate() {
        for (String cname : new ArrayList<>(classes.keySet())) {
            dfs(cname);
        }
    }

    private void dfs(String cname) {
        ClassInfo ci = classes.get(cname);
        if (ci.vis)
            return; // already done

        // visit parent first
        if (ci.class_parent != null) {
            dfs(ci.class_parent);
            ClassInfo p = classes.get(ci.class_parent);

            // copy parent's final field list & offsets
            ci.final_vars_hashed = new ArrayList<>(p.final_vars_hashed);
            // ci.final_var_offset = new LinkedHashMap<>(p.final_var_offset);
            // fix
            ci.declared_class_vars_tohashed = new LinkedHashMap<>(p.declared_class_vars_tohashed);
            ci.hashed_var_types = new LinkedHashMap<>(p.hashed_var_types);
            ci.declared_var_offset = new LinkedHashMap<>(p.declared_var_offset);
            // fix
            // copy parent's vtable
            ci.vtable = new ArrayList<>(p.vtable);
            ci.vtable_index = new LinkedHashMap<>(p.vtable_index);
        } else {
            ci.final_vars_hashed = new ArrayList<>();
            // ci.final_var_offset = new LinkedHashMap<>();
            ci.vtable = new ArrayList<>();
            ci.vtable_index = new LinkedHashMap<>();
        }

        // append declared fields
        for (String original : ci.declared_class_vars) {
            String hashed_var = ci.declared_class_vars_tohashed.get(original);
            ci.final_vars_hashed.add(hashed_var);
            int idx = ci.final_vars_hashed.size() - 1; // 0-based among fields
            ci.declared_var_offset.put(hashed_var, 4 * (1 + idx)); // offset: 4*(1 + idx); 0 reserved for vtable ptr
        }
        ci.total_var_count = ci.final_vars_hashed.size();

        // methods: produce labels and assign/override vtable slots
        for (Map.Entry<String, MethodInfo> e : ci.declared_methods.entrySet()) {
            String mname = e.getKey();
            MethodInfo mi = e.getValue();
            mi.label = ci.class_name + "_" + mname;
            mi.param_count = mi.method_params.size();

            if (ci.vtable_index.containsKey(mname)) {
                // override parent's slot
                int slot = ci.vtable_index.get(mname);
                ci.vtable.set(slot, mi.label);
                mi.index = slot;
            } else {
                // new method: append to vtable
                ci.vtable.add(mi.label);
                int slot = ci.vtable.size() - 1;
                ci.vtable_index.put(mname, slot);
                mi.index = slot;
            }
        }

        ci.vis = true;
    }

    // function to get the filed offset
    public int getFieldOffset(String static_class_name, String orig_field_name) {
        String changed = HashVar(static_class_name, orig_field_name);
        // find the owner class (changed format is ClassName$orig)
        int sep = changed.indexOf('$');
        String ownerClass = changed.substring(0, sep);
        ClassInfo owner = classes.get(ownerClass);
        return owner.declared_var_offset.get(changed);
    }

    public void printInfo() {
        for (var ce : classes.entrySet()) {
            ClassInfo c = ce.getValue();
            System.out.println("Class " + c.class_name + (c.class_parent != null ? " extends " + c.class_parent : ""));
            System.out.println(" Fields :");
            for (String mangled : c.final_vars_hashed) {
                int sep = mangled.indexOf('$');
                String owner = sep >= 0 ? mangled.substring(0, sep) : "(?)";
                String orig = sep >= 0 ? mangled.substring(sep + 1) : mangled;
                int off = classes.get(owner).declared_var_offset.get(mangled);
                System.out.println("   " + mangled + " (" + orig + " declared in " + owner + ") @ " + off);
            }
            System.out.println(" VTable:");
            for (int i = 0; i < c.vtable.size(); ++i)
                System.out.println("   [" + i + "] " + c.vtable.get(i));
            System.out.println(" Declared methods:");
            for (var me : c.declared_methods.entrySet()) {
                MethodInfo m = me.getValue();
                System.out.println("   " + m.method_name + " label=" + m.label + " slot=" + m.index + " params="
                        + m.param_count);
            }
            System.out.println();
        }
    }
}