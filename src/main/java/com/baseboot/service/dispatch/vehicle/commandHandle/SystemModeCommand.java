package com.baseboot.service.dispatch.vehicle.commandHandle;

import com.baseboot.common.service.DelayedService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.entry.global.LogType;
import com.baseboot.enums.AutoDriveApplyForStateEnum;
import com.baseboot.enums.DriveTypeEnum;
import com.baseboot.enums.ModeStateEnum;
import com.baseboot.enums.WhetherEnum;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 车载系统模式处理
 */
@Data
@Slf4j
@MonitorAttrCommand
public class SystemModeCommand implements MonitorAttrHandle {

    private Integer vehicleId;

    private final VehicleTaskHelper helper;

    private volatile boolean receiveApplyFor = false;//是否收到自动驾驶申请

    private DriveTypeEnum preSystemType;//前一个系统模式

    public SystemModeCommand(VehicleTaskHelper helper) {
        this.vehicleId = helper.getVehicleId();
        this.helper = helper;
    }

    public void receiveCommand(Monitor monitor) {
        systemModeAppleFor(monitor);
        setSystemMode(monitor);
    }

    /**
     * 自动模式授权
     */
    public void autoModeImPower() {
        String key = TimerCommand.VEHICLE_AUTO_APPLY_FOR + vehicleId;
        synchronized (helper) {
            if (BaseUtil.isExistLikeTask(key)) {
                return;
            }
        }
        if (!receiveApplyFor) {
            log.debug("【{}】未收到自动授权申请", vehicleId);
            return;
        }
        log.debug("【{}】自动模式授权", vehicleId);
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_MANUAL_APPLY_FOR + vehicleId);
        DelayedService.Task task = DelayedService.buildTask();
        task.withTask(() -> {
            if (!receiveApplyFor) {
                task.withExec(false);
                return;
            }

            Monitor monitor = helper.getLiveInfo().getMonitor();
            if (null != monitor) {
                DriveTypeEnum systemMode = monitor.getSystemMode();
                if (DriveTypeEnum.AUTOMATIC_DRIVE.equals(systemMode)) {
                    task.withExec(false);//是自动模式，任务结束
                    log.debug("【{}】系统模式设置,value=【{}】", vehicleId, systemMode.getDesc());
                    helper.getVehicleMonitorManager().changeModeStateEnum(ModeStateEnum.SELF_MODE);
                    systemModeHandle();
                    return;
                }
                CommSend.vehAutoMode(vehicleId, AutoDriveApplyForStateEnum.AUTOMATIC_DRIVE_APPLICATION);
            }
        }).withTaskId(key).withDelay(1000).withAtOnce(true).withNum(60)
                .withPrintLog(false)
                .withAfterTask(() -> {
                    if (task.getNum() == 0) {
                        log.error("【{}】自动模式授权定时过期!!!", vehicleId);
                        systemModeHandle();
                    }
                });
        DelayedService.addTask(task);
    }


    /**
     * 人工模式授权
     */
    public void manualModeImPower() {
        String key = TimerCommand.VEHICLE_MANUAL_APPLY_FOR + vehicleId;
        synchronized (helper) {
            if (BaseUtil.isExistLikeTask(key)) {
                return;
            }
        }

        log.debug("【{}】人工模式授权", vehicleId);
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_AUTO_APPLY_FOR + vehicleId);
        receiveApplyFor = true;
        DelayedService.Task task = DelayedService.buildTask();
        task.withTask(() -> {
            Monitor monitor = helper.getLiveInfo().getMonitor();
            if (null != monitor) {
                DriveTypeEnum systemMode = monitor.getSystemMode();
                if (DriveTypeEnum.MANUAL_DRIVE.equals(systemMode)) {
                    task.withExec(false);
                    log.debug("【{}】系统模式设置,value=【{}】", vehicleId, systemMode.getDesc());
                    helper.getVehicleMonitorManager().changeModeStateEnum(ModeStateEnum.MANUAL_MODE);
                    receiveApplyFor = false;
                    return;
                }
                if (DriveTypeEnum.AUTOMATIC_DRIVE.equals(systemMode)) {//是自动驾驶模式，转为人工
                    CommSend.vehAutoMode(vehicleId, AutoDriveApplyForStateEnum.MANUAL_DRIVE_APPLICATION);
                }
            }
        }).withTaskId(key).withDelay(1000).withAtOnce(true).withNum(60)
                .withPrintLog(false)
                .withAfterTask(() -> {
                    if (task.getNum() == 0) {
                        log.error("【{}】人工模式授权定时过期!!!", vehicleId);
                        systemModeHandle();
                    }

                });
        DelayedService.addTask(task);
    }

    /**
     * 系统模式申请自动驾驶
     */
    private void systemModeAppleFor(Monitor monitor) {
        WhetherEnum applyForAutoMode = monitor.getApplyForAutoMode();
        if (WhetherEnum.YES.equals(applyForAutoMode)) {
            synchronized (BaseCacheUtil.objectLock("systemModeAppleFor-" + vehicleId)) {
                if (!receiveApplyFor && !helper.getVehicleMonitorManager().isAutoDriveMode()) {
                    log.debug("【{}】申请自动驾驶!!!", vehicleId);
                    setReceiveApplyFor(true);
                    String format = BaseUtil.format("【{}】收到[自动驾驶模式申请]", vehicleId);
                    LogUtil.addLogToRedis(LogType.INFO, "monitor-command-" + vehicleId, format);
                }
            }
        }
    }

    public void setReceiveApplyFor(boolean receiveApplyFor) {
        this.receiveApplyFor = receiveApplyFor;
    }

    /**
     * 系统模式设置
     */
    private void setSystemMode(Monitor monitor) {
        DriveTypeEnum systemMode = monitor.getSystemMode();
        if (null == systemMode || systemMode.equals(this.preSystemType)) {
            return;
        }
        log.warn("【{}】系统驾驶模式更变:当前:【{}】", vehicleId, systemMode.getDesc());
        String format = BaseUtil.format("【{}】当前车控制模式:[{}]", vehicleId, systemMode.getDesc());
        LogUtil.addLogToRedis(LogType.INFO, "monitor-systemMode-" + vehicleId, format);
        this.preSystemType = systemMode;
        if (!helper.getVehicleMonitorManager().isModeState(ModeStateEnum.MANUAL_MODE, ModeStateEnum.SELF_MODE)) {
            return;
        }
        boolean flag = false;
        switch (systemMode) {
            case MANUAL_DRIVE:
                flag = helper.getVehicleMonitorManager().changeModeStateEnum(ModeStateEnum.MANUAL_MODE);
                break;
            case AUTOMATIC_DRIVE:
            case MANNED_DRIVE:
                flag = helper.getVehicleMonitorManager().changeModeStateEnum(ModeStateEnum.SELF_MODE);
                break;
        }
        if (flag) {
            helper.getVehicleStateManager().stopTask();
        }
    }

    /**
     * 系统模式处理,命令改变，发送复位命令
     */
    private void systemModeHandle() {
        String key = TimerCommand.VEHICLE_NONE_APPLY_FOR + vehicleId;
        DelayedService.Task task = DelayedService.buildTask();
        task.withTask(() -> {
            log.debug("【{}】系统模式改变，发送【复位命令】", vehicleId);
            BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_AUTO_APPLY_FOR + vehicleId);
            BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_MANUAL_APPLY_FOR + vehicleId);
            CommSend.vehAutoMode(vehicleId, AutoDriveApplyForStateEnum.NONE);
        }).withTaskId(key).withDelay(1000).withAtOnce(true).withNum(5).withPrintLog(true);
        DelayedService.addTask(task);
    }
}
