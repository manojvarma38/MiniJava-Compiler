import java.util.function.Function;

class Main {
  public static void main(String[] args) {
    System.out.println(new RunnerMany().run());
  }
}

class RunnerMany {
  public int run() {
    Base bBase;
    Base bL1;
    Base bL2;
    Base bL3;
    Level1 l1;
    Level2 l2;
    Level3 l3;
    int r1;
    int r2;
    int r3;
    int r4;
    int u1;
    int u2;
    int u3;

    bBase = new Base();
    bL1 = new Level1();
    bL2 = new Level2();
    bL3 = new Level3();

    l1 = new Level1();
    l2 = new Level2();
    l3 = new Level3();

    r1 = bBase.manyArgs(1, 2, (x) -> x + 1, (y) -> y + 2, 3, (z) -> z * 0);
    r2 = bL1.manyArgs(1, 2, (x) -> x + 1, (y) -> y * 2, 3, (z) -> z + 5);
    r3 = bL2.manyArgs(2, 3, (x) -> x * 2, (y) -> y + 1, 4, (z) -> z - 1);
    r4 = bL3.manyArgs(3, 1, (x) -> x + 0, (y) -> y * 5, 2, (z) -> z + 10);

    u1 = l1.level1Only(5);
    u2 = l2.level2Only(6);
    u3 = l3.level3Only(7);

    return ((r1 + r2) + (r3 + r4)) + ((u1 + u2) + u3);
  }
}

class Base {
  public int manyArgs(int a, int b, Function<Integer,Integer> f1, Function<Integer,Integer> f2, int c, Function<Integer,Integer> f3) {
    int r1;
    int r2;
    r1 = f1.apply(a);
    r2 = f2.apply(b);
    System.out.println((r1 + r2) + c);
    return (r1 + r2) + c;
  }
  public int baseOnly(int x) {
    int v;
    v = x + 1;
    System.out.println(v);
    return v;
  }
}

class Level1 extends Base {
  public int manyArgs(int a, int b, Function<Integer,Integer> f1, Function<Integer,Integer> f2, int c, Function<Integer,Integer> f3) {
    int r1;
    int r2;
    r1 = f1.apply(a);
    r2 = f2.apply(b);
    System.out.println((r1 + r2) + (c + 10));
    return (r1 + r2) + (c + 10);
  }
  public int level1Only(int x) {
    int v;
    v = x + 100;
    System.out.println(v);
    return v;
  }
}

class Level2 extends Level1 {
  public int manyArgs(int a, int b, Function<Integer,Integer> f1, Function<Integer,Integer> f2, int c, Function<Integer,Integer> f3) {
    int r1;
    int r2;
    int r3;
    r1 = f1.apply(a);
    r2 = f2.apply(b);
    r3 = f3.apply(c);
    System.out.println((r1 + r2) + r3);
    return r1 + (r2 + r3);
  }
  public int level2Only(int x) {
    int v;
    v = x + 200;
    System.out.println(v);
    return v;
  }
}

class Level3 extends Level2 {
  public int manyArgs(int a, int b, Function<Integer,Integer> f1, Function<Integer,Integer> f2, int c, Function<Integer,Integer> f3) {
    int r1;
    int r2;
    int r3;
    r1 = f1.apply(a);
    r2 = f2.apply(b);
    r3 = f3.apply(c);
    System.out.println((r1 + r2) * r3);
    return (r1 + r2) * r3;
  }
  public int level3Only(int x) {
    int v;
    v = x + 300;
    System.out.println(v);
    return v;
  }
}
