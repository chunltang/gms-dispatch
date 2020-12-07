package com.baseboot.service.calculate;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 计算类型
 * */
public enum CalculateTypeEnum {

    PATH_LENGTH_LIMITING("0","允许发送的路径长度限制"),
    VEHICLE_PATH_POSITION_LIMITING("1","跟随限制"),
    VEHICLE_NOTIFY_STOP_LIMITING("2","其他车辆通知该车的限制"),
    OBSTACLE_LIMITING("3","障碍物添加对车辆移动限制"),
    VEHICLE_DISPUTEAREA_LIMITING("4","争用区限制"),
    VEHICLE_ABSOLUTE_DISTANCE_LIMITING("5","位置绝对值计算");

    private String value;

    private String desc;

    CalculateTypeEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @JsonValue
    public String getValue() {
        return this.value;
    }

    public String getDesc() {
        return this.desc;
    }
}
