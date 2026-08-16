class ArrayOps {
    public static void main(String[] a) {
        int[] x;
        int n;
        x = new int[5];
        n = 0;
        while (n <= 4) {
            x[n] = n + 10;
            n = n + 1;
        }
        System.out.println(x[2]);
        System.out.println(x.length);
    }
}