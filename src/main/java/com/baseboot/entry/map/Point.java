package com.baseboot.entry.map;

import com.baseboot.common.utils.BaseUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Point {

    private double x;

    private double y;

    private double z;

    private double yawAngle;


    public Point() {

    }

    public Point(double x, double y, double z, double yawAngle) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yawAngle = yawAngle;
    }

    @JsonProperty("yaw_angle")
    public double getYawAngle() {
        return this.yawAngle;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Point) {
            Point point = (Point) obj;
            if (Math.abs(this.x - point.getX()) < 0.01 && Math.abs(this.y - point.getY()) < 0.01) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return BaseUtil.format("x:{},y:{},z:{},angle:{}", x, y, z, yawAngle);
    }
}
