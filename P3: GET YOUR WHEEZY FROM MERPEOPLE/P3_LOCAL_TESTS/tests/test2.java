import java.util.function.Function;

class Main {
  public static void main(String[] args) {
    System.out.println(new Runner1().run());
  }
}

/* Runner1 demonstrates a lambda (increment) and calls overridden methods below */
class Runner1 {
  public int run() {
    Function<Integer,Integer> inc;
    A a;
    a = new B();
    inc = (n) -> n + (a.compute(n));
    return inc.apply(5);   
  }
}

/* Base/Derived to show overriding (not used directly in Runner1, but present to satisfy requirement) */
class A {
  public int compute(int x) {
    return x;            // base behavior
  }
}

class B extends A {
  public int compute(int x) {
    return x * 2;        // overriding behavior (same signature as A.compute)
  }
}
