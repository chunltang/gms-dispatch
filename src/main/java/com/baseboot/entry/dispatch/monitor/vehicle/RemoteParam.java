package com.baseboot.entry.dispatch.monitor.vehicle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 远程控制参数
 * */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class RemoteParam {

    private int vehicleId;

    private double dValue;

    private int iValue;
}
