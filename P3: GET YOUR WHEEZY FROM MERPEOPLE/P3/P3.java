import syntaxtree.*;
import visitor.*;

public class P3 {
    public static void main(String[] args) {
        try {
            Node root = new MiniJavaParser(System.in).Goal();

            ProgInfo prog_info = new ProgInfo();

            // collect info about inheritance, fields and methods
            Collector collector = new Collector(prog_info);
            root.accept(collector, null);

            prog_info.accumulate();// add all inherited fields and methods in the jar

            // prog_info.printInfo();

            // IR code emitter
            IRCodeEmitter irce = new IRCodeEmitter(prog_info);
            root.accept(irce, null);

            // Lambda code

        } catch (ParseException e) {
            System.out.println("nahh, no wayy!");
        }

    }
}