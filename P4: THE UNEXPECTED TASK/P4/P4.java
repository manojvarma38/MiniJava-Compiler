import syntaxtree.*;
import visitor.*;

public class P4 {
    public static void main(String[] args) {
        try {
            new MiniIRParser(System.in);
            Node root = MiniIRParser.Goal();
            IR_Converter v = new IR_Converter();
            root.accept(v, null);

        } catch (ParseException e) {
            System.out.println("nahh, no wayy!");
        }

    }
}