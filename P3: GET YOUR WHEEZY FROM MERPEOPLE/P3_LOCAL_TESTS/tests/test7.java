import java.util.function.Function;

class Main {
  public static void main(String[] args) {
    System.out.println(new RunnerLR().run());
  }
}

class RunnerLR {
  public int run() {
    Top t;
    Function<Integer,Integer> fg;
    int v;
    int res;
    t = new Bottom();
    fg = t.make();
    v = fg.apply(10);
    res = (((t.make()).apply(7)) + (t.useMake(5))) + v;
    return res;
  }
}

class Top {
  public Function<Integer,Integer> make() {
    return (x) -> x + 1;
  }

  public int useMake(int v) {
    Function<Integer,Integer> f;
    f = this.make();
    return f.apply(v);
  }
}

class Middle extends Top {
  public Function<Integer,Integer> make() {
    return (x) -> x * 2;
  }

  public int useMake(int v) {
    Function<Integer,Integer> f;
    f = this.make();
    return (f.apply(v)) + 1;
  }
}

class Bottom extends Middle {
  public Function<Integer,Integer> make() {
    return (x) -> x - 3;
  }
  
  public int useMake(int v) {
    Function<Integer,Integer> f;
    f = this.make();
    return (f.apply(v)) * 2;
  }
}
