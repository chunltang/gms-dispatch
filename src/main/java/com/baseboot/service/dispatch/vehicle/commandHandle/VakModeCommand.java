package com.baseboot.service.dispatch.vehicle.commandHandle;

import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.enums.ModeStateEnum;
import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 车辆上报控制模式
 */
@Data
@Slf4j
@MonitorAttrCommand
public class VakModeCommand implements MonitorAttrHandle{

    private Integer vehicleId;

    private VehicleTaskHelper helper;

    /**
     * 当前上报模式
     */
    private ModeStateEnum curReportedMode;

    /**
     * 当前车辆控制模式
     */
    private ModeStateEnum curControlMode = ModeStateEnum.MANUAL_MODE;

    public VakModeCommand(VehicleTaskHelper helper) {
        this.vehicleId = helper.getVehicleId();
        this.helper = helper;
    }

    public void receiveCommand(Monitor monitor) {
        ModeStateEnum newMode = ModeStateEnum.getEnum(String.valueOf(monitor.getVakMode()));
        if (null == newMode) {
            log.error("控制模式没有对应处理逻辑!");
            return;
        }
        if (!newMode.equals(curReportedMode)) {
            //log.debug("车辆【{}】上报控制模式改变,newMode=【{}】,oldMode=【{}】", vehicleId, newMode.getDesc(), null != curReportedMode ? curReportedMode.getDesc() : "");
            switch (newMode) {
                case CONN:
                    connMode();
                    break;
                case SILENT:
                    silentMode();
                    break;
                case MANUAL_MODE:
                    manualMode();
                    break;
                case SELF_MODE:
                    selfMode();
                    break;
                case REMOTE_MODE:
                    remoteMode();
                    break;
                case SELF_INSPECTION:
                    selfInspectionMode();
                    break;
                case ERROR:
                    error();
                    break;
            }
            curReportedMode = newMode;
        }
        modeSwitch();
    }

    /**
     * 自动命令控制模式切换
     */
    private void modeSwitch() {
        if (!helper.getVehicleStateManager().isFreeState()) {
            return;
        }
        TaskCodeEnum taskCode = helper.getVehicleMonitorManager().getTaskCode();
        if (null != taskCode) {
            switch (taskCode) {
                case TASKSTANDBY:
                case TASKDRIVING:
                case TASKUNLOADSOIL:
                case TASKLOAD:
                case TASKEMERGENCYPARKBYLINE:
                case TASKEMERGENCYPARKBYTRAJECTORY:
                case TASKNORMALPARKBYTRAJECTORY:
                    //切换自动模式
                    if (helper.getVehicleMonitorManager().isAutoDriveMode()) {
                        setCurControlMode(ModeStateEnum.SELF_MODE);
                    }
                    break;
                case TASKREMOTECONTROL:
                    //切换远程模式
                    if (helper.getVehicleMonitorManager().isAutoDriveMode()) {
                        setCurControlMode(ModeStateEnum.REMOTE_MODE);
                    }
                    break;
                case HEARTBEAT:
                case TASKLAUNCHMOTOR:
                    //切换人工模式
                    setCurControlMode(ModeStateEnum.MANUAL_MODE);
                    break;
                case TASKSELFCHECK:
                    //自检
                    setCurControlMode(ModeStateEnum.SELF_INSPECTION);
                    break;

            }
        }
    }

    public ModeStateEnum getCurControlMode() {
        return curControlMode;
    }

    private void setCurControlMode(ModeStateEnum curControlMode) {
        if (!curControlMode.equals(this.curControlMode) || !helper.getVehicleMonitorManager().getModeState().equals(curControlMode)) {
            this.curControlMode = curControlMode;
            log.debug("【{}】自动命令控制模式切换,value=【{}】", vehicleId, curControlMode.getDesc());
            helper.getVehicleMonitorManager().changeModeStateEnum(curControlMode);
        }
    }

    /**
     * 收到请求连接命令
     */
    private void connMode() {
        log.debug("车辆[{}]收到【请求连接模式命令】", vehicleId);
    }

    /**
     * 收到静默控制模式命令
     */
    private void silentMode() {
        log.debug("车辆[{}]收到【静默控制模式命令】", vehicleId);
    }

    /**
     * 收到手动控制模式命令
     */
    private void manualMode() {
        log.debug("车辆[{}]收到【手动控制模式命令】", vehicleId);
    }

    /**
     * 收到自动控制模式命令
     */
    private void selfMode() {
        log.debug("车辆[{}]收到【自动控制模式命令】", vehicleId);
    }

    /**
     * 收到远程控制模式命令
     */
    private void remoteMode() {
        log.debug("车辆[{}]收到【远程控制模式命令】", vehicleId);
    }

    /**
     * 收到自检控制模式命令
     */
    private void selfInspectionMode() {
        log.debug("车辆[{}]收到【自检控制模式命令】", vehicleId);
    }

    /**
     * 控制模式异常
     */
    private void error() {
        log.debug("车辆[{}]收到【控制模式异常命令】", vehicleId);
    }
}
