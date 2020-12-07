package com.baseboot.entry.dispatch.area;

import com.baseboot.entry.map.Point;
import lombok.Data;

/**
 * 排队点
 * */
@Data
public class QueuePoint {

    private Long queueId;

    private Point queuePoint;

    public QueuePoint(Long queueId, Point queuePoint) {
        this.queueId = queueId;
        this.queuePoint = queuePoint;
    }

    public QueuePoint(Long queueId, double x, double y, double z, double yawAngle) {
        this.queueId = queueId;
        this.queuePoint = new Point(x, y, z, yawAngle);
    }

    @Override
    public String toString(){
        return "排队点:queueId="+queueId+",point="+queuePoint.toString();
    }
}
