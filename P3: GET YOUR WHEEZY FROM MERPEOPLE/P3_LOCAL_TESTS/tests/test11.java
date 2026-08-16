import java.util.function.Function;

class Main {
  public static void main(String[] args) {
    System.out.println(new RunnerCalls().run());
  }
}

class RunnerCalls {
  public int run() {
    SuperType sSub;
    SuperType sSuper;
    int rSub;
    int rSuper;
    sSub = new SubType();
    sSuper = new SuperType();
    rSub = sSub.combine(4, (x) -> x + 1, (y) -> y * 3);
    rSuper = sSuper.modify(5, (z) -> z + 2);
    System.out.println(rSub);
    System.out.println(rSuper);
    return (rSub * 100) + rSuper;
  }
}

class SuperType {
  public int combine(int base, Function<Integer,Integer> f1, Function<Integer,Integer> f2) {
    int a;
    a = f1.apply(base);
    return f2.apply(a);
  }
  public int modify(int base, Function<Integer,Integer> f) {
    Function<Integer,Integer> inc;
    inc = (n) -> n + 1;
    return inc.apply(f.apply(base));
  }
}

class SubType extends SuperType {
  public int combine(int base, Function<Integer,Integer> f1, Function<Integer,Integer> f2) {
    int a;
    a = f1.apply(base);
    return (f2.apply(a)) + 10;
  }
  public int modify(int base, Function<Integer,Integer> f) {
    return (f.apply(base)) * 2;
  }
}
