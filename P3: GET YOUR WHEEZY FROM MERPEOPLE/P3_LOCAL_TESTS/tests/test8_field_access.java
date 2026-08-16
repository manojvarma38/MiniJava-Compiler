class Fields {
    public static void main(String[] a) {
        System.out.println(new F().doit());
    }
}

class F {
    int x;
    int y;

    public int doit() {
        x = 5;
        y = 7;
        return x + y;
    }
}