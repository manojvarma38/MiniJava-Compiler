import syntaxtree.*;
import visitor.*;

public class P5 {
    public static void main(String[] args) {
        try {
            new microIRParser(System.in);
            Node root = microIRParser.Goal();
            // Pass 1: collect
            Collector p1 = new Collector();
            IRProgram prog = p1.run(root);
            // Pass 2+3:
            // Liveness Analysis
            // Register Allocation using Linearscan
            // Emit
            Emitter emitter = new Emitter();
            emitter.emit(prog, System.out);

        } catch (ParseException e) {
            System.out.println("nahh, no wayy!");
        }
    }
}