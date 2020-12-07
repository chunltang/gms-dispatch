package com.baseboot.service.calculate;

public class CalculateConfig {

    /**
     * 预警范围
     */
    public final static double WARN_RANGE = 200;

    /**
     * 进入该范围才判断争用区的限制
     */
    public final static double DISPUTE_RANGE = 50;

    /**
     * 距离争用区距离
     * */
    public final static double DISPUTE_DISTANCE = 15;

    /**
     * 真实位置和最近点位置偏差
     * */
    public final static double PATH_DEVIATION = 1.5;
}
