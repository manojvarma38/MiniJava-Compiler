import java.util.function.Function;

class Main {
    public static void main(String[] args) {
        System.out.println(new Runner3().run());
    }
}

class Runner3 {
    public int run() {
        int[] a;
        Function<Integer,Integer> dbl;
        Parent par;
        a = new int[3];
        a[0] = 7;
        dbl = (n) -> n * 2;
        par = new Second_level();
        System.out.println(par.value(a[0]));
        par = new First_level();
        System.out.println(par.value(a[0]));
        return dbl.apply(a[0]);   
    }
}

class Parent {
    public int value(int x) {
        return x + 1;
    }
}

class First_level extends Parent {
    public int value(int x) {
        return x + 10;
    }
}

class Second_level extends First_level {
    public int value(int x) {
        return x + 100;   
    }
}
