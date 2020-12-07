package com.baseboot.entry.dispatch.monitor.excavator;

import com.baseboot.entry.dispatch.monitor.LiveInfo;
import com.baseboot.entry.map.Point;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExcavatorGpsInfo extends LiveInfo implements Serializable {

    private static final long serialVersionUID = -2692695204849609645L;

    /**
     * 电铲编号
     */
    private Integer excavatorId;

    /**
     * 生命信号
     * */
    private int liveSign;

    /**
     * 电铲坐标
     */
    private double ex;
    private double ey;
    private double ez;

    private double eAngle;//横摆角
    private double uAngle;//俯仰角
    private double pAngle;//翻滚角

    /**
     * 铲斗坐标
     */
    private double dx;
    private double dy;
    private double dz;
    private double dAngle;

    /**
     * gps状态
     * */
    private int state;

    /**
     * 获取当前位置
     */
    public Point getCurPoint() {
        return new Point(ex, ey, ez, eAngle);
    }

    /**
     * 获取铲斗位置
     */
    public Point getBucketPoint() {
        return new Point(dx, dy, dz, dAngle);
    }
}
