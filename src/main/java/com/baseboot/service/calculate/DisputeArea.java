package com.baseboot.service.calculate;

import lombok.Data;

import java.util.*;

/**
 * 争用区
 */
@Data
public class DisputeArea {

    private long id;

    private double x;//争用区位置x

    private double y;//争用区位置y

    /**
     * 是否被锁定
     */
    private volatile boolean lock = false;

    /**
     * 占用对象编号
     */
    private Integer uniqueId;

    /**
     * 哪些对象的争用区
     */
    private List<Integer> useObjects = new ArrayList<>();

    /**
     * 每个车在争用区的路径索引
     * */
    private Map<Integer, Integer> indexMap;

    /**
     * 添加时间
     */
    private long addTime;

    /**
     * 判断是否是同一个争用区
     */
    public boolean judgeEqualsArea(DisputeArea other) {
        return null != other && Math.abs(other.getX() - x) < 0.5 && Math.abs(other.getY() - y) < 0.5;
    }

    public boolean judgeEqualsArea(double ox, double oy) {
        return Math.abs(ox - x) < 0.5 && Math.abs(oy - y) < 0.5;
    }

    /**
     * 设置占用对象
     */
    public boolean setOccupyObj(Integer uniqueId) {
        if (useObjects.contains(uniqueId) && !lock) {
            this.lock = true;
            this.uniqueId = uniqueId;
            return true;
        }
        return false;
    }

    /**
     * 添加争用对象
     */
    public void addDisputeObj(Integer uniqueId) {
        if (null != uniqueId) {
            useObjects.add(uniqueId);
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof DisputeArea) {
            DisputeArea disputeArea = (DisputeArea) obj;
            return x == disputeArea.getX() && y == disputeArea.getY() && addTime == disputeArea.getAddTime();
        }
        return false;
    }
}
