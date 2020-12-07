package com.baseboot.entry.dispatch.monitor.excavator;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 电铲下发数据结构
 */
@Data
public class ExcavatorSendMessage {

    private int sign;

    private Integer excavatorId;

    private int state;//任务区状态

    private int code;

    private double ox;
    private double oy;
    private double oz;//tcl 改为装载点

    private double ex;
    private double ey;
    private double ez;
    private double eAngle;

    private double dx;
    private double dy;
    private double dz;
    private double dAngle;

    private int relevanceId;

    //private double relevanceDis;//关联距离

    private int size;

    private List<VehicleInfo> vehicles = new ArrayList<>();


    @Data
    public static class VehicleInfo {

        private int vehicleId;
        private double x;
        private double y;
        private double z;
        private double yawAngle;
        private double speed;
        private int arriveTime;

        private int dispatchState = 1;//矿车调度状态（地面）
        private int dispatchModeState = 0;//矿车模式状态（地面）
        private int dispatchTaskState = 6;//矿车调度状态（地面）

        private int vehicleTaskState = 0;//车载任务状态（车载)
        private int vehicleModeState = 0;//车载模式状态（车载）

        //private double loadCapacity;//载重
        //网路强度
    }
}
