class test1 {
	public static void main(String[] args) {
		System.out.println(new A().start(10));
	}
}

class A {
	public int start(int x) {
		System.out.println(x);
		{
			x = 6;
			System.out.println(x);
		}
		System.out.println(x);
		return 1;
	}
}