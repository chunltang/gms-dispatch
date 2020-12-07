package com.baseboot.entry.dispatch.area;

import com.baseboot.entry.map.Point;
import lombok.Data;

/**
 * 卸载点
 * */
@Data
public class UnloadPoint {

    private Long unloadId;

    private Point unloadPoint;

    public UnloadPoint(Long unloadId, Point unloadPoint) {
        this.unloadId = unloadId;
        this.unloadPoint = unloadPoint;
    }

    public UnloadPoint(Long unloadId, double x,double y,double z,double yawAngle) {
        this.unloadId = unloadId;
        this.unloadPoint = new Point(x,y,z,yawAngle);
    }

    @Override
    public String toString(){
        return "卸载点:unloadId="+unloadId+",point="+unloadPoint.toString();
    }
}
