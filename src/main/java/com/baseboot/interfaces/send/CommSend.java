package com.baseboot.interfaces.send;

import com.alibaba.fastjson.JSONObject;
import com.baseboot.common.config.QueueConfig;
import com.baseboot.common.service.DelayedService;
import com.baseboot.common.service.MqService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorSendMessage;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.LogType;
import com.baseboot.entry.global.Request;
import com.baseboot.entry.map.OriginPoint;
import com.baseboot.enums.AutoDriveApplyForStateEnum;
import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.enums.VehicleCommandEnum;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.input.InputCache;
import com.baseboot.service.dispatch.vehicle.CommandStateManager;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CommSend {

    private final static long DELAY_TIME = 2000;

    private static CommandStateManager getVehicleCommandManager(Integer vehicleId) {
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            return vehicleTask.getHelper().getCommandStateManager();
        }
        return null;
    }

    /**
     * 发送自动模式授权指令
     */
    public static void vehAutoMode(Integer vehicleId, AutoDriveApplyForStateEnum command) {
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        params.put("command", command);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            log.debug("【{}】发送车载系统模式命令,command=【{}】", vehicleId, command.getDesc());
            commandManager.issueCommand(VehicleCommandEnum.VEHATUOMODE, BaseUtil.toJson(params), false);
        }
    }

    /**
     * 发送电铲心跳
     */
    public static void excavatorSend(ExcavatorSendMessage message) {
        Request request = new Request().withMessage(BaseUtil.toJson(message)).withRouteKey("excavatorSend").withToWho(QueueConfig.REQUEST_COMM).withNeedPrint(false);
        MqService.request(request);
    }

    /**
     * 发送心跳
     */
    public static void heartBeat(Integer vehicleId) {
        OriginPoint point = BaseCacheUtil.getOriginPoint();
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask && vehicleTask.getHelper().isCollectionVehicle()) {
            point.setX(0D);
            point.setY(0D);
            point.setZ(0D);
        }
        JSONObject json = new JSONObject();
        json.put("vehicleId", vehicleId);
        json.put("origin", point);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            commandManager.issueCommand(VehicleCommandEnum.HEARTBEAT, json.toJSONString(), false);
        }
    }

    /**
     * 直线停车
     */
    public static void vehRemoteEmergencyParking(int vehicleId) {
        DelayedService.addTaskNoExist(() -> {
            vehRemoteEmergencyParkingTimer(vehicleId);
        }, DELAY_TIME, TimerCommand.VEHICLE_EMERGENCY_PARKING_COMMAND + vehicleId, true).withNum(10);
    }

    private static void vehRemoteEmergencyParkingTimer(int vehicleId) {
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        //不是自动模式，车停没有运行轨迹，车辆不是轨迹跟随状态
        if (!isSelfMode(vehicleId) || !vehicleTask.getHelper().getRunStateManager().isStart() || !vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKDRIVING)) {
            BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_EMERGENCY_PARKING_COMMAND + vehicleId);
            return;
        }

        InputCache.getDispatchInput(vehicleId).stopRun();
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            commandManager.issueCommand(VehicleCommandEnum.TASKEMERGENCYPARKBYLINE, BaseUtil.toJson(params), true);
        }
    }

    /**
     * 清除紧急停车
     */
    public static void vehAutoClearEmergencyPark(int vehicleId) {
        DelayedService.addTaskNoExist(() -> {
            vehAutoClearEmergencyParkTimer(vehicleId);
        }, DELAY_TIME, TimerCommand.VEHICLE_CLEAR_EMERGENCY_PARKING_COMMAND + vehicleId, true).withNum(10);
    }

    private static void vehAutoClearEmergencyParkTimer(int vehicleId) {
        String key = TimerCommand.VEHICLE_CLEAR_EMERGENCY_PARKING_COMMAND + vehicleId;
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null == vehicleTask ||
                !vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKEMERGENCYPARKBYTRAJECTORY, TaskCodeEnum.TASKEMERGENCYPARKBYLINE)) {
            String format = BaseUtil.format("【{}】下发[缓解紧急停车]成功", vehicleId);
            LogUtil.addLogToRedis(LogType.INFO,"monitor-command-"+vehicleId,format);
            BaseUtil.cancelDelayTask(key);
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            commandManager.issueCommand(VehicleCommandEnum.VEHAUTOCLEAREMERGENCYPARK, BaseUtil.toJson(params), true);
        }
    }


    /**
     * 等待，进入待机状态
     */
    public static void vehAutoStandby(Integer vehicleId) {
        DelayedService.addTaskNoExist(() -> {
            vehAutoStandbyTimer(vehicleId);
        }, DELAY_TIME, TimerCommand.VEHICLE_AUTO_STANDBY_COMMAND + vehicleId, true).withNum(1);
    }

    private static void vehAutoStandbyTimer(Integer vehicleId) {
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            //车辆运行后不能发送待机
            if (!vehicleTask.getHelper().getRunStateManager().isRunning()) {
                commandManager.issueCommand(VehicleCommandEnum.VEHAUTOSTANDBY, BaseUtil.toJson(params), true);
            }
        }
    }

    /**
     * 安全停车
     */
    public static void vehAutoSafeParking(Integer vehicleId) {
        if (null == vehicleId) {
            return;
        }
        DelayedService.addTaskNoExist(() -> {
            vehAutoSafeParkingTimer(vehicleId);
        }, DELAY_TIME, TimerCommand.VEHICLE_SAFE_STOP_COMMAND + vehicleId, true).withNum(5);
    }

    private static void vehAutoSafeParkingTimer(Integer vehicleId) {
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null == vehicleTask) {
            return;
        }
        vehicleTask.getHelper().getVehicleStateManager().stopTask();
        if (isSelfMode(vehicleId)) {
            //只有在运行状态发送安全停车才有意义
            Map<String, Object> params = new HashMap<>();
            params.put("vehicleId", vehicleId);
            CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
            if (null != commandManager) {
                if (!vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKEMERGENCYPARKBYLINE, TaskCodeEnum.TASKEMERGENCYPARKBYTRAJECTORY)) {
                    commandManager.issueCommand(VehicleCommandEnum.VEHAUTOSAFEPARKING, BaseUtil.toJson(params), true);
                }
            }
        } else {
            BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_SAFE_STOP_COMMAND + vehicleId);
        }
    }

    /**
     * 轨迹跟随,只有在待机和轨迹跟随状态下才能发
     */
    public static void vehAutoTrailFollowing(Integer vehicleId, byte[] bytes) {
        if (!isSelfMode(vehicleId)) {
            return;
        }
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleId && vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKSTANDBY, TaskCodeEnum.TASKDRIVING, TaskCodeEnum.TASKDATASAVE, TaskCodeEnum.TASKLOAD)) {
            CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
            if (null != commandManager) {
                commandManager.issueCommand(VehicleCommandEnum.VEHAUTOTRAILFOLLOWING, bytes, true);
            }
        }
    }

    /**
     * 装载制动
     */
    public static void vehAutoLoadBrake(Integer vehicleId, Integer value) {
        if (!isSelfMode(vehicleId)) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        params.put("value", value);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            commandManager.issueCommand(VehicleCommandEnum.VEHAUTOLOADBRAKE, BaseUtil.toJson(params), true);
        }
    }

    /**
     * 卸矿
     */
    public static void vehAutoUnload(Integer vehicleId) {
        DelayedService.addTaskNoExist(() -> {
            vehAutoUnloadTimer(vehicleId);
        }, 2000, TimerCommand.VEHICLE_AUTO_UNLOAD_COMMAND + vehicleId, true).withNum(10);
    }

    private static void vehAutoUnloadTimer(Integer vehicleId) {
        if (!isSelfMode(vehicleId)) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            commandManager.issueCommand(VehicleCommandEnum.VEHAUTOUNLOAD, BaseUtil.toJson(params), true);
        }
    }

    /**
     * 装载
     */
    public static void vehAutoLoad(Integer vehicleId) {
        DelayedService.addTaskNoExist(() -> {
            vehAutoLoadTimer(vehicleId);
        }, 1000, TimerCommand.VEHICLE_AUTO_LOAD_COMMAND + vehicleId, true).withNum(5);
    }

    private static void vehAutoLoadTimer(Integer vehicleId) {
        if (!isSelfMode(vehicleId)) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            //如果收到装载，则取消定时器
            if (vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKLOAD)) {
                BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_AUTO_LOAD_COMMAND + vehicleId);
                return;
            }
            //待机状态下才能发装载
            if (vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKSTANDBY)) {
                commandManager.issueCommand(VehicleCommandEnum.VEHAUTOLOAD, BaseUtil.toJson(params), true);
            }
        }
    }

    private static boolean isSelfMode(Integer vehicleId) {
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        boolean selfMode = vehicleTask.getHelper().getVehicleMonitorManager().isSelfMode();
        if (!selfMode) {
            log.error("车辆【{}】不是自动模式，不能发送自动模式命令", vehicleId);
        }
        return selfMode;
    }

    /**
     * 排土
     */
    public static void vehAutoDump(Integer vehicleId) {
        if (!isSelfMode(vehicleId)) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            commandManager.issueCommand(VehicleCommandEnum.VEHAUTODUMP, BaseUtil.toJson(params), true);
        }
    }

    /**
     * 启动发动机
     */
    public static void vehAutoStart(Integer vehicleId) {
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            commandManager.issueCommand(VehicleCommandEnum.VEHAUTOSTART, BaseUtil.toJson(params), true);
        }
    }

    /**
     * 关闭发动机
     */
    public static void vehAutoStop(Integer vehicleId) {
        Map<String, Object> params = new HashMap<>();
        params.put("vehicleId", vehicleId);
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            commandManager.issueCommand(VehicleCommandEnum.VEHAUTOSTOP, BaseUtil.toJson(params), true);
        }
    }


    public static void sendAnyCommand(int command) {
        int vehicleId = 998;
        CommandStateManager commandManager = getVehicleCommandManager(vehicleId);
        if (null != commandManager) {
            commandManager.setTest(true);
        }
        switch (command) {
            case 0:
                //直线停车
                vehRemoteEmergencyParking(vehicleId);
                break;
            case 1:
                //清除紧急停车
                vehAutoClearEmergencyPark(vehicleId);
                break;
            case 2:
                //进入待机状态
                vehAutoStandby(vehicleId);
                break;
            case 3:
                //安全停车
                vehAutoSafeParking(vehicleId);
                break;
            case 4:
                //卸矿
                vehAutoUnload(vehicleId);
                break;
            case 5:
                //装载
                BaseCacheUtil.getVehicleTask(vehicleId).changeStartFlag(false);
                vehAutoLoad(vehicleId);
                break;
            case 6:
                //启动发动机
                vehAutoStart(vehicleId);
                break;
            case 7:
                //关闭发动机
                vehAutoStop(vehicleId);
                break;
            case 8:
                //复位
                CommSend.vehAutoMode(vehicleId, AutoDriveApplyForStateEnum.NONE);
                break;
        }
    }
}
