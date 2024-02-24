public class Classes {
    public static void main(String[] args) {
        Base b;
        Derived d;
        int x;
        b = new Base();
        d = new Derived();
        b = d;
        System.out.println(b.set(1));
        System.out.println(b.set(3));
        if (d) {
            int e;
        }
        if (b < 5) {
            int e;
        }
    }
}
class Rest extends Base implements Nothing {
}
class Base extends Rest implements Face,MyFace {
    int[] data;
    private Rest[] d;
    private int set(int[] x) {
        data = x;
        return data;
    }
    public int get() {
        return data;
    }
    private int test() {
        while (1) {
            int x;
        }
        int b;
        int t;
        a = 0;
        b = 9;
        if (true) {
            boolean n;
        }
        return 5;
    }
    private int getFace(int s) {
        return 5;
    }
}
class Derived extends Base {
    public int set(int x, C d) {
        data = x * 2;
        Base[] t;
        if (false && x) {
        }
        return data;
    }
}
class Hi {
    private void sayHi(int one, boolean two, Hi three, int four) {
    }
}
interface Face {
    final int[] a = {1, 2};
    public abstract int getFace(int s);
}
interface MyFace {
    private String b = new String();
}
