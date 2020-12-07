package com.baseboot.service.dispatch.optimalDispatch;

import lombok.Data;

/**
 * 矿车自身参数
 */
@Data
public class VehicleParam {

    /**
     * 车编号
     */
    private Integer vehicleId;


    /**
     * 最小安全距离
     */
    private double minSafeDistance;

    /**
     * 最短紧急制动距离
     */
    private double minEmergencyBrakingDistance;

    /**
     * 最高运行速度
     */
    private double maxSpeed;

    /**
     * 最大加速度
     */
    private double maxAcceleration;

    /**
     * 最大减速度
     */
    private double maxDeceleration;

    /**
     * 冲击率
     */
    private double impactRate = 0.75;

    /**
     * D/R挡位切换时间
     */
    private double switchGearTime = 3;
}
