class Main {
    public static void main(String[] args) {
        System.out.println((new Caller().call(new SuperType())) + (new Caller().call(new SubType())));
    }
}

class SuperType {
    public int value() {
        return 10;
    }
}

class SubType extends SuperType {
    public int value() {
        return 20; 
    }
}

class Caller {
    public int call(SuperType s) {
        return s.value(); 
    }
}
