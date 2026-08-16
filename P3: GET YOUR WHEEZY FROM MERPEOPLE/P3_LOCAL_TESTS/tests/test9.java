import java.util.function.Function;

class Main {
    public static void main(String[] args) {
        System.out.println(
            // deeply nested call with different argument types:
            new C().compute(
                new B().combine(
                    5 + (new A().inc(2)),  // AddExpression + MessageSend (int)
                    false                // boolean
                ),
                new A().applyFunc(
                    (x) -> x              // LambdaExpression returning the passed A
                ),
                new D()                  // object of class D
            )
        );
    }
}

class A {
    public int inc(int n) {
        return n + 1;
    }

    public int applyFunc(Function<A, A> f) {
        return (f.apply(this)).inc(2);
    }
}

class B {
    // takes an int and a boolean (multiple-typed args)
    public int combine(int val, boolean flag) {
        // flag is accepted to exercise boolean argument type; result uses val
        return val + 2;
    }
}

class D {
    public int times(int a, int b) {
        return a * b;
    }
}

class C {
    // accepts: int, int, and an object D (demonstrates object-type parameter)
    public int compute(int p, int q, D d) {
        // uses parameters and an object method call (nested call with object param)
        return (p * q) + (d.times(p, q));
    }
}
