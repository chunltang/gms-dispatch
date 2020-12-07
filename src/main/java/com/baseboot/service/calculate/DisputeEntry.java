package com.baseboot.service.calculate;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 争用区
 */
@Data
public class DisputeEntry {

    private long id;

    private double x;//争用区位置x

    private double y;//争用区位置y

    private int vehicleId;

    public DisputeEntry(long id, double x, double y, int vehicleId) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.vehicleId = vehicleId;
    }
}
