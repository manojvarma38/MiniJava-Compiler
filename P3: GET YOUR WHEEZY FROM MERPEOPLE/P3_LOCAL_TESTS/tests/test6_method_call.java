class Call {
    public static void main(String[] a) {
        System.out.println(new C().run());
    }
}

class C {
    public int run() {
        return this.helper(3, 4);
    }

    public int helper(int x, int y) {
        return x + y * 2;
    }
}