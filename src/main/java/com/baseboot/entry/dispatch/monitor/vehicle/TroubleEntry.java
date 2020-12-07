package com.baseboot.entry.dispatch.monitor.vehicle;

import com.baseboot.common.utils.DateUtil;
import lombok.Data;

@Data
public class TroubleEntry {

    private int vehicleId;

    private String storeTime= DateUtil.formatStringFullTime();

    private String vehicleTime;

    private DeviceTrouble[] deviceDiag;

    private String[] troubleDesc;
}
