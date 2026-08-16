class Chain {
    public static void main(String[] a) {
        System.out.println(new C1().a());
    }
}

class C1 {
    public int a() {
        return this.b(10) + 1;
    }

    public int b(int k) {
        return this.c(k - 3);
    }

    public int c(int m) {
        return m;
    }
}