import java.util.function.Function;

class Main {
  public static void main(String[] args) {
    System.out.println(new RunnerC().run());
  }
}

class RunnerC {
  public int run() {
    int[] nums;
    int idx;
    int x;
    Function<Integer,Integer> transform;
    GBase gb;
    GMid gm;
    GBottom gbottom;

    nums = new int[2];
    nums[0] = 3;
    nums[1] = 7;
    idx = (nums.length) - 1;   

    gbottom = new GBottom();
    transform = (v) -> v * (gbottom.getMul(2)); 
    nums[1] = transform.apply(nums[1]);      

    if (((nums[0]) <= (nums[1])) && (!((nums[0]) != 3))) {
      nums[0] = (nums[0]) + (gbottom.getAdd(4));
    } else {
      nums[0] = (nums[0]) - 1;
    }

    gb = new GBottom(); 
    gm = new GBottom();  
    x = ((gb.getAdd(1)) + (gm.getMul(1))) + (nums[idx]); 

    return x;
  }
}

class GBase {
  public int getAdd(int p) {
    return p + 1;
  }
  public int getMul(int p) {
    return p + 0;
  }
}

class GMid extends GBase {
  public int getAdd(int p) {    
    return p + 5;
  }
  public int extraMid(int p) {
    return p * 2;
  }
}

class GBottom extends GMid {
  public int getMul(int p) {    
    return p * 3;
  }
}
