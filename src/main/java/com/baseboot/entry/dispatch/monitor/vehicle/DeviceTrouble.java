package com.baseboot.entry.dispatch.monitor.vehicle;

import lombok.Data;

/**
 * 设备诊断
 * */
@Data
public class DeviceTrouble {

    /**
     * 部件编码
     * */
    private int devCode;

    /**
     * 故障编码
     * */
    private int diagCode;

    public DeviceTrouble() {

    }

    public DeviceTrouble(int devCode, int diagCode) {
        this.devCode = devCode;
        this.diagCode = diagCode;
    }
}
