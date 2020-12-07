package com.baseboot.entry.dispatch.area;

import com.baseboot.entry.map.Point;
import lombok.Data;

/**
 * 装载点
 */
@Data
public class LoadPoint {

    private Long loadId;

    private Point loadPoint;

    public LoadPoint(Long loadId, Point loadPoint) {
        this.loadId = loadId;
        this.loadPoint = loadPoint;
    }

    public LoadPoint(Long loadId, double x, double y, double z, double yawAngle) {
        this.loadId = loadId;
        this.loadPoint = new Point(x, y, z, yawAngle);
    }

    @Override
    public String toString(){
        return "装载点:loadId="+loadId+",point="+loadPoint.toString();
    }
}
