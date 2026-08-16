import java.util.function.Function;

class Factorial {
	public static void main(String[] a) {
		System.out.println(new A().init()); // Initially it was 10.
		// MiniJava/MicroJava code uses the older expression.
	}
}

class A {
	Fac2 f;

	public int init() {
		int y;
		Fac t;
		f = new Fac2();
		y = f.init2();
		y = f.init();

		System.out.println(f.test2());
		System.out.println(f.test((x) -> (x + 10)));

		t = f;

		System.out.println(t.test1());
		System.out.println(t.test((x) -> (x + 10)));

		return f.test1();
	}
}

class Fac {
	int a;

	public int init() {
		a = 5;
		return 5;
	}

	public int test1() {
		return a;
	}

	public int test(Function<Integer, Integer> g) {
		return g.apply(a);
	}
}

class Fac2 extends Fac {
	int a;

	public int init2() {
		a = 7;
		return 7;
	}

	public int test2() {
		return a;
	}

	public int test(Function<Integer, Integer> g) {
		return g.apply(a);
	}
}

class B {
	int f;

	public Function<Integer, Integer> method() {
		Function<Integer, Integer> t;
		f = 5;
		t = (x) -> (x + f);
		f = 7;
		return t;
	}
}