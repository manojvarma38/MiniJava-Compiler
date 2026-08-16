class Inherit {
    public static void main(String[] a) {
        System.out.println(new B().go());
    }
}

class A {
    public int go() {
        return 1;
    }

    public int twice() {
        return 2;
    }
}

class B extends A {
    public int go() {
        return this.twice() + 3;
    }
}