import syntaxtree.*;
import visitor.*;

public class P2 {
    public static void main(String[] args) {
        try {
            Node root = new MiniJavaParser(System.in).Goal();

            ProgInfo prog_info = new ProgInfo();

            // collect and store variables ,classes
            Collector collector = new Collector(prog_info);
            root.accept(collector, null);
            // prog_info.printInfo();

            // OVERLOADING CHECK
            OverloadingChecker overloadingChecker = new OverloadingChecker(prog_info);
            root.accept(overloadingChecker, null);

            // type checking
            TypeChecker tc = new TypeChecker(prog_info);
            root.accept(tc, null);

            System.out.println("Program type checked successfully");

        } catch (MyException.TypeError e) {
            System.out.println("Type error");
            // e.printStackTrace();
        } catch (MyException.SymbolNotFound e) {
            System.out.println("Symbol not found");
            // e.printStackTrace();
        } catch (Exception e) {
            // e.printStackTrace();
            System.out.println("Type error");
            // e.printStackTrace();
        }
    }
}