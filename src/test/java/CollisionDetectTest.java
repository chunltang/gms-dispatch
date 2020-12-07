import com.baseboot.common.utils.CollisionDetectUtil;

import com.baseboot.common.utils.CollisionDetectUtil.Rectangle;
public class CollisionDetectTest {



    public static void main(String[] args) {
        long l = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            Rectangle ra = new Rectangle(0.9f, 1, (float) Math.sqrt(2), (float) Math.sqrt(2), 45);
            Rectangle rb = new Rectangle(3f, 1, 2, 2, 0);
            Boolean aBoolean = CollisionDetectUtil.collisionDetect(ra, rb);
            System.out.println(aBoolean);
        }
        System.out.println("time=" + (System.currentTimeMillis() - l));

        Rectangle ra1 = new Rectangle(1, 1, 2, 2, 45);
        Rectangle rb1 = new Rectangle(3.0F, 3.01f, 2, 2, 0);
        Boolean aBoolean1 = CollisionDetectUtil.collisionDetectByAngle(ra1, rb1);
        System.out.println(aBoolean1);
    }
}
