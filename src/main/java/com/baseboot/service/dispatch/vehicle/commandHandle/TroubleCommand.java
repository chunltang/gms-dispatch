package com.baseboot.service.dispatch.vehicle.commandHandle;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.DeviceTrouble;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.entry.dispatch.monitor.vehicle.TroubleEntry;
import com.baseboot.entry.dispatch.monitor.vehicle.TroubleParse;
import com.baseboot.entry.global.MongoKeyPool;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.task.MongoStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * 车载上报故障诊断
 */
@Data
@Slf4j
@MonitorAttrCommand
public class TroubleCommand implements MonitorAttrHandle {

    private Integer vehicleId;

    private VehicleTaskHelper helper;

    private String preTroubleDesc;


    public TroubleCommand(VehicleTaskHelper helper) {
        this.vehicleId = helper.getVehicleId();
        this.helper = helper;
    }

    public void receiveCommand(Monitor monitor) {
        DeviceTrouble[] deviceDiag = monitor.getVecTrouble();
        if (BaseUtil.arrayNotNull(deviceDiag)) {
            String[] troubleDesc = new String[deviceDiag.length];
            for (int i = 0; i < deviceDiag.length; i++) {
                DeviceTrouble trouble = deviceDiag[i];
                TroubleParse entry = BaseCacheUtil.getTroubleEntry(String.valueOf(trouble.getDevCode()));
                if (null == entry) {
                    log.error("故障设备编号不存在,code=[{}]", trouble.getDevCode());
                    continue;
                }
                String diagDesc = entry.getKeyVal().get(String.valueOf(trouble.getDiagCode()));
                troubleDesc[i] = entry.getDesc() + diagDesc;
            }
            helper.getMonitor().setTroubleDesc(troubleDesc);
            String desc = Arrays.toString(troubleDesc);
            if (desc.equals(preTroubleDesc)) {
                return;
            }
            preTroubleDesc = desc;
            TroubleEntry troubleEntry = new TroubleEntry();
            troubleEntry.setVehicleId(vehicleId);
            troubleEntry.setTroubleDesc(troubleDesc);
            troubleEntry.setDeviceDiag(deviceDiag);
            troubleEntry.setVehicleTime(monitor.timeToString());
            MongoStore.addToMongo(MongoKeyPool.VEHICLE_TROUBLE, troubleEntry);
            //log.warn("【{}】车载上报故障,数量:[{}],描述：{}", vehicleId, deviceDiag.length, desc);
        }
    }
}
