import java.util.function.Function;

class Main {
  public static void main(String[] args) {
    System.out.println(new RunnerB().run());
  }
}

/* Three-level inheritance:
   TopBase  <- Middle  <- Bottom
   - TopBase defines alpha(int) and beta(int)
   - Middle overrides alpha(int) and adds gamma(int)
   - Bottom overrides beta(int)
   We call via polymorphic variables so runtime dispatch demonstrates overriding at different levels.
*/

class RunnerB {
  public int run() {
    TopBase tb;
    Middle md;
    Bottom bt;
    int r1;
    int r2;
    int r3;

    // polymorphism: reference of TopBase to a Bottom instance
    tb = new Bottom();          // tb.alpha -> dispatch to Middle.alpha (overridden there)
    md = new Bottom();          // md.gamma -> Middle.gamma (defined in Middle)
    bt = new Bottom();          // bt.beta  -> Bottom.beta (overridden here)

    r1 = tb.alpha(4);           // alpha overridden in Middle => uses Middle.alpha
    r2 = md.gamma(5);           // gamma defined in Middle (not overridden)
    r3 = bt.beta(6);            // beta overridden in Bottom

    // combine results: ensures methods coming from different levels contribute
    return (r1 + r2) + r3;        // integer sum
  }
}

class TopBase {
  public int alpha(int x) {
    Function<Integer,Integer> f;
    f = (y) -> y + 1;
    return f.apply(x);  // base: x + 1
  }
  public int beta(int x) {
    return x + 2;       // base: x + 2
  }
}

class Middle extends TopBase {
  public int alpha(int x) {   // overrides TopBase.alpha
    // different behavior here
    Function<Integer,Integer> f;
    f = (z) -> z * 2;
    return f.apply(x); // middle: x * 2
  }
  public int gamma(int x) {   // new at middle level
    return x + 10;
  }
}

class Bottom extends Middle {
  public int beta(int x) {    // overrides TopBase.beta (at bottom level)
    return x * 3;
  }
}
