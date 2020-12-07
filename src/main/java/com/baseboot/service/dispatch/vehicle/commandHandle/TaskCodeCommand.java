package com.baseboot.service.dispatch.vehicle.commandHandle;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.entry.global.*;
import com.baseboot.enums.NotifyCommandEnum;
import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.task.TaskStateEnum;
import com.baseboot.service.dispatch.task.TriggerTask;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 车辆上报命令类型处理
 * {@link TaskCodeEnum}
 */
@Data
@Slf4j
@MonitorAttrCommand
public class TaskCodeCommand extends AbstractEventPublisher implements MonitorAttrHandle {

    private Integer vehicleId;

    private VehicleTaskHelper helper;

    private volatile TaskCodeEnum curCode;//处理命令之前的命令

    private volatile TaskCodeEnum preCode;//前一个指令

    private int step = 0;//命令执行间隔

    private static Map<Integer, Set<TriggerTask>> taskMap = new ConcurrentHashMap<>();

    private volatile boolean standbySign = false;//待机标志位，true为可以发送待机

    private LimitQueue<TaskCodeEnum> taskCodeQueue = new LimitQueue<>(100);

    public TaskCodeCommand(VehicleTaskHelper helper) {
        this.vehicleId = helper.getVehicleId();
        this.helper = helper;
    }

    public void receiveCommand(Monitor monitor) {
        TaskCodeEnum newCode = TaskCodeEnum.getEnum(String.valueOf(monitor.getCurrentTaskCode()));
        if (null == newCode) {
            log.error("车辆【{}】TaskCode输入异常", vehicleId);
            return;
        }
        taskCodeQueue.add(newCode);
        handleStandby(newCode);
        this.step++;
        this.preCode = this.curCode;
        this.curCode = newCode;
        if (!curCode.equals(preCode) || step > 5) {
            step = 0;
            switch (newCode) {
                case HEARTBEAT:
                    heartbeat();
                    break;
                case TASKSTANDBY:
                    taskStandby();
                    break;
                case TASKSELFCHECK:
                    taskSelfCheck();
                    break;
                case TASKDRIVING:
                    taskDriving();
                    break;
                case TASKUNLOADSOIL:
                    taskUnloadMine();
                    break;
                case TASKNORMALPARKBYTRAJECTORY:
                    taskNormalParkByTrajectory();
                    break;
                case TASKEMERGENCYPARKBYTRAJECTORY:
                    taskEmergencyParkByTrajectory();
                    break;
                case TASKEMERGENCYPARKBYLINE:
                    taskEmergencyParkByline();
                    break;
                case TASKCLOSEMOTOR:
                    taskCloseMotor();
                    break;
                case TASKSTATICTEST:
                    taskStaticTest();
                    break;
                case TASKDATASAVE:
                    taskDataSave();
                    break;
                case TASKLOAD:
                    taskLoad();
                    break;
                case MANUALCONTROL:
                case DRIVERCONTROL:
                    driverMode();
                    break;

                default:
                    log.error("上传指令没有处理逻辑,mode={}", newCode.getDesc());
            }

            if (standbySign) {
                standbySign = false;
                sendAutoStandby();
            }
            //记录日志
            String format = BaseUtil.format("【{}】矿卡报文,任务类型:【{}】,报文时间:【{}】,序列号:【{}】，位置:【{}】",
                    vehicleId,
                    curCode.getDesc(),
                    helper.getMonitor().timeToString(),
                    helper.getMonitor().getLockedDeviceCode(),
                    helper.getMonitor().positionToString());
            LogUtil.addLogToFile(LogType.VEHICLE_TASK_TYPE, vehicleId, format);
            printCommandLog();
        }
        triggerTask();
    }

    /**
     * 判断前几个命令中是否包含指定命令
     */
    public boolean isContains(int limit, TaskCodeEnum taskCode) {
        if (taskCodeQueue.limitContains(limit, taskCode)) {
            log.warn("【{}】判断前几个命令中是否包含指定命令：{}", vehicleId, taskCode.getDesc());
            return true;
        }
        return false;
    }


    /**
     * 触发任务
     */
    private void triggerTask() {
        if (taskMap.containsKey(vehicleId)) {
            Iterator<Set<TriggerTask>> iterator = taskMap.values().iterator();
            while (iterator.hasNext()) {
                Set<TriggerTask> set = iterator.next();
                set.removeIf((t) -> !TaskStateEnum.FAIL.equals(t.execTask()));
                if (set.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 发送待机命令
     */
    private void sendAutoStandby() {
        if (null != curCode && (curCode.equals(TaskCodeEnum.TASKSTANDBY))) {
            CommSend.vehAutoStandby(vehicleId);
        }
    }

    /**
     * 需要收到3次待机命令，特殊处理
     * 在命令赋值之前执行
     */
    private void handleStandby(TaskCodeEnum newCode) {
        if (newCode.equals(TaskCodeEnum.TASKSTANDBY) &&
                newCode.equals(curCode) &&
                curCode.equals(preCode)) {
            //清除发送待机命令定时器
            standbySign = true;
            if (isContains(10, TaskCodeEnum.TASKUNLOADSOIL)) {
                //如果是VAK卸矿转为待机，则发送卸矿完成通知
                eventPublisher(EventType.VEHICLE_TASKCODE, NotifyCommandEnum.VEHICLE_UNLOAD_END_COMMAND);
                helper.getVehicleStateManager().getCommandStateManager().cancelPreCode();
            }
            BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_AUTO_STANDBY_COMMAND + vehicleId);
        } else {
            standbySign = false;
        }
    }


    /**
     * 收到心跳命令
     */
    private void heartbeat() {
        log.debug("车辆[{}]收到默认【心跳命令】", vehicleId);
    }

    /**
     * 收到待机命令
     */
    private void taskStandby() {
        log.debug("车辆[{}]收到【待机命令】", vehicleId);
        WorkPathInfo workPathInfo = BaseCacheUtil.getWorkPathInfo(vehicleId);
        if (null != workPathInfo) {
            if (curCode.equals(TaskCodeEnum.TASKSTANDBY) && isContains(5, TaskCodeEnum.TASKDRIVING)) {//收到待机,判断路径到达分段终点,并且收到过轨迹跟随
                workPathInfo.permitSelectionRun();
            }
        }
        //进入待机，清除安全停车定时器
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_SAFE_STOP_COMMAND + vehicleId);
        //清除发送待机命令定时器
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_AUTO_STANDBY_COMMAND + vehicleId);
    }

    /**
     * 收到自检命令
     */
    private void taskSelfCheck() {
        log.debug("车辆[{}]收到【自检命令】", vehicleId);
    }

    /**
     * 收到轨迹跟随命令
     */
    private void taskDriving() {
        log.debug("车辆[{}]收到【轨迹跟随命令】", vehicleId);
    }

    /**
     * 收到原路径安全停车命令
     */
    private void taskNormalParkByTrajectory() {
        log.debug("车辆[{}]收到【原路径安全停车命令】", vehicleId);
        //清除定时器
        helper.getVehicleStateManager().stopTask();
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_SAFE_STOP_COMMAND + vehicleId);
    }

    /**
     * 收到原路径紧急停车命令
     */
    private void taskEmergencyParkByTrajectory() {
        log.warn("车辆[{}]收到【原路径紧急停车命令】", vehicleId);
        //清除定时器
        helper.getVehicleStateManager().stopTask();
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_EMERGENCY_PARKING_COMMAND + vehicleId);
    }

    /**
     * 收到直线急停命令
     */
    private void taskEmergencyParkByline() {
        log.debug("车辆[{}]收到【直线急停命令】", vehicleId);
        helper.getVehicleStateManager().stopTask();
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_EMERGENCY_PARKING_COMMAND + vehicleId);
    }

    /**
     * 收到卸矿命令
     */
    private void taskUnloadMine() {
        log.debug("车辆[{}]收到【卸矿命令】", vehicleId);
        eventPublisher(EventType.VEHICLE_TASKCODE, NotifyCommandEnum.VEHICLE_UNLOAD_START_COMMAND);
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_AUTO_UNLOAD_COMMAND + vehicleId);

    }

    /**
     * 收到关闭发动机命令
     */
    private void taskCloseMotor() {
        log.debug("车辆[{}]收到【关闭发动机命令】", vehicleId);
    }

    /**
     * 收到静态测试
     */
    private void taskStaticTest() {
        log.debug("车辆[{}]收到【静态测试命令】", vehicleId);
    }

    /**
     * 收到驻车制动
     */
    private void taskDataSave() {
        log.debug("车辆[{}]收到【驻车制动命令】", vehicleId);
    }

    /**
     * 装载
     */
    private void taskLoad() {
        log.debug("车辆[{}]收到【装载命令】", vehicleId);
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_AUTO_LOAD_COMMAND + vehicleId);
    }

    /**
     * 驾驶模式指令
     */
    private void driverMode() {
        log.debug("车辆[{}]收到【驾驶模式指令】,curCode=【{}】", vehicleId, this.curCode.getDesc());
    }


    @Override
    public void eventPublisher(EventType eventType, Object value) {
        for (Listener listener : getListeners()) {
            listener.stateChange(eventType, value);
        }
    }

    public static void addTask(Integer vehicleId, TriggerTask task) {
        if (null != vehicleId && null != task) {
            taskExist(task.getTaskId());
            taskMap.computeIfAbsent(vehicleId, s -> Collections.synchronizedSet(new HashSet<TriggerTask>())).add(task);
        }
    }

    /**
     * 打印重要指令日志
     */
    private void printCommandLog() {
        if (!curCode.equals(preCode)) {
            String format = BaseUtil.format("【{}】当前控制指令：[{}]", vehicleId, curCode.getDesc());
            LogUtil.addLogToRedis(LogType.WARN, "monitor-command-" + vehicleId, format);
        }
    }

    /**
     * 存在则直接替换
     */
    private static void taskExist(String taskId) {
        for (Set<TriggerTask> tasks : taskMap.values()) {
            for (TriggerTask task : tasks) {
                if (task.getTaskId().equals(taskId)) {
                    task.setTaskState(TaskStateEnum.EXPIRATION);
                    log.warn("任务替换，taskId={}", task.getTaskId());
                }
            }
        }
    }

}
