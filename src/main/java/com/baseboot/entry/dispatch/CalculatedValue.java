package com.baseboot.entry.dispatch;

/**
 * 车辆计算阈值...
 */
public class CalculatedValue {

    /**
     * 判断到达终点的阈值
     */
    public final static double END_DISTANCE_THRESHOLD = 4;//m

    /**
     * 到终点剩余点
     * */
    public final static int END_POINT_NUMS = 40;

    /**
     * 车辆安全行驶距离
     * */
    public final static int TWO_POINT_DISTANCE = 1;//m

    /**
     * 矿车碰撞检查外扩安全距离
     * */
    public final static int COLLISION_DETECT_DISTANCE = 1;//m

    /**
     * 到碰撞点的防碰撞点数
     * */
    public final static int COLLISION_POINT_NUMS = 300;//300*0.1 30m
}
