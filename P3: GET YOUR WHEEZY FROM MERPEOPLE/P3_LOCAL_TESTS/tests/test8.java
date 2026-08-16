import java.util.function.Function;

class Main {
  public static void main(String[] args) {
    System.out.println(new RunnerArgs().run());
  }
}

class RunnerArgs {
  public int run() {
    Processor p;
    int result;
    p = new Processor();
    result = p.process(5, (x) -> x + 2, (y) -> y * 3);
    return result;
  }
}

class Processor {
  public int process(int base, Function<Integer,Integer> f1, Function<Integer,Integer> f2) {
    int a;
    int b;
    a = f1.apply(base);
    b = f2.apply(a);
    return b + base;
  }
}
