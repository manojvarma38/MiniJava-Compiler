import syntaxtree.*;
import visitor.*;

public class P6 {
   public static void main(String[] args) {
      try {
         Node root = new MiniRAParser(System.in).Goal();
         // System.out.println("Program parsed successfully");
         root.accept(new Pass1(), null);
      } catch (ParseException e) {
         System.out.println(e.toString());
      }
   }
}
