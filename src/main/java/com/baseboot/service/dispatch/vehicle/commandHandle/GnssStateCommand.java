package com.baseboot.service.dispatch.vehicle.commandHandle;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.entry.global.LimitQueue;
import com.baseboot.entry.global.LogType;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 车载GPS故障诊断
 */
@Data
@Slf4j
@MonitorAttrCommand
public class GnssStateCommand implements MonitorAttrHandle {

    private Integer vehicleId;

    private VehicleTaskHelper helper;

    private LimitQueue<Integer> gnssQueue = new LimitQueue<>(10);

    private volatile int preGnssState;


    public GnssStateCommand(VehicleTaskHelper helper) {
        this.vehicleId = helper.getVehicleId();
        this.helper = helper;
    }

    public void receiveCommand(Monitor monitor) {
        int gnssState = monitor.getGnssState();
        gnssQueue.add(gnssState);
        if (42 != gnssState) {
            String key = "monitor-gps-error-" + vehicleId;
            LogUtil.printLog(() -> {
                String format = BaseUtil.format("【{}】当前GNSS异常状态为[{}]", vehicleId, gnssState);
                log.error(format);
                if (gnssState != preGnssState) {
                    LogUtil.addLogToRedis(LogType.ERROR, key, format);
                }
            }, key, 5000);
            if (gnssQueue.limitContainsNum(5, gnssState, 5)) {
                if (helper.getRunStateManager().isStart()) {
                    String format = BaseUtil.format("【{}】当前GNSS状态为[{}]，下发安全停车!", vehicleId, gnssState);
                    LogUtil.addLogToRedis(LogType.ERROR, key, format);
                    log.error(format);
                    CommSend.vehAutoSafeParking(vehicleId);
                }
            }
        } else if (gnssState != preGnssState) {
            if (gnssQueue.limitContainsNum(1, gnssState, 0)) {
                String key = "monitor-gps-" + vehicleId;
                String format = BaseUtil.format("【{}】当前GNSS状态为[42]", vehicleId);
                LogUtil.addLogToRedis(LogType.INFO, key, format);
                log.debug(format);
            }
        }
        preGnssState = gnssState;
    }
}
