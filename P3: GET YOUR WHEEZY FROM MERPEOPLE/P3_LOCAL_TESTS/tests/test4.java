import java.util.function.Function;

class Main {
  public static void main(String[] args) {
    System.out.println(new RunnerA().run());
  }
}

class RunnerA {
  public int run() {
    int[] arr;
    int a;
    int b;
    int c;
    int d;
    Function<Integer,Integer> add3;

    arr = new int[4];
    arr[0] = 20;
    arr[1] = 5;
    a = arr[0];               // 20
    b = arr[1];               // 5

    // arithmetic: -, +, *, / using bracket expressions to nest (grabs many forms)
    c = a - b;                // 15
    d = (a * (b + 2)) / (b - 1); // (20 * 7) / 4 = 35

    // lambda: add 3
    add3 = (x) -> x + 3;
    arr[2] = add3.apply(d);  // arr[2] = 38

    // use array assignment and lookup combined with function result
    arr[3] = ((arr[0]) / (arr[1])) + ((arr[2]) - c); // (20/5) + (38-15) = 4 + 23 = 27

    // boolean tests in statements (uses <=, !=, &&, ||, !)
    if ((a <= (b * 5))) {            // 20 <= 25 -> true
      a = a + 1;                     // a becomes 21
    } else {
      a = a - 1;
    }

    if ((!(a != 21)) || (b <= 0)) {  // !(21 != 21) -> true  short-circuit
      b = b + 0;
    }

    // array lookup and length used in final arithmetic expression
    // return arr[3] + c + arr[2] - a  => 27 + 15 + 38 - 21 = 59
    return (((arr[3]) + c) + (arr[2])) - a;
  }
}
