package com.baseboot.service.dispatch.manager;

import ch.qos.logback.core.util.DelayStrategy;
import com.baseboot.common.service.DelayedService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.area.LoadArea;
import com.baseboot.entry.dispatch.area.UnLoadMineralArea;
import com.baseboot.entry.dispatch.area.UnloadArea;
import com.baseboot.entry.dispatch.monitor.vehicle.Unit;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleLiveInfo;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.BaseCache;
import com.baseboot.entry.global.EventType;
import com.baseboot.entry.global.Listener;
import com.baseboot.entry.map.Point;
import com.baseboot.enums.*;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.input.DispatchUnitInput;
import com.baseboot.service.dispatch.input.DispatchUnitStateEnum;
import com.baseboot.service.dispatch.input.InputCache;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 调度状态管理
 */
@Data
@Slf4j
public class UnitTaskStateManager implements Listener {

    private Integer vehicleId;

    private DispatchUnitInput unitInput;

    private NotifyCommandEnum curWaitCommand = NotifyCommandEnum.NONE_COMMAND;


    public UnitTaskStateManager(Integer vehicleId, DispatchUnitInput unitInput) {
        this.vehicleId = vehicleId;
        this.unitInput = unitInput;
    }

    /**
     * 调度任务状态改变,只能设置为去装载排队点/卸载区排队点
     */
    public void changeUnitState(DispatchUnitStateEnum state) {
        if (null != state) {
            switch (state) {
                case GO_LOAD_QUEUE_POINT://去装载排队点
                    goLoadQueuePoint();
                    break;
                case LOAD_WAIT_INTO_COMMAND://等待进车信号
                    loadWaitIntoCommand();
                    break;
                case GO_LOAD_TASK_POINT://去装载点
                    goLoadTaskPoint();
                    break;
                case PREPARE_LOAD://等待装车信号
                    prepareLoad();
                    break;
                case EXEC_LOAD://执行装载
                    execLoad();
                    break;
                case GO_UNLOAD_QUEUE_POINT://去卸载排队点
                    goUnLoadQueuePoint();
                    break;
                case UNLOAD_WAIT_INTO_COMMAND://等待卸载区允许进入
                    unloadWaitIntoCommand();
                    break;
                case GO_UNLOAD_TASK_POINT://前往卸载点
                    goUnloadTaskPoint();
                    break;
                case PREPARE_UNLOAD://准备卸载
                    prepareUnload();
                    break;
                case EXEC_UNLOAD://执行卸载
                    execUnload();
                    break;
                default:
                    log.error("没有对应可执行调度任务!");
            }
        }
    }

    /********************************** 装载 ****************************************/

    /**
     * 前往装载区排队点
     */
    private void goLoadQueuePoint() {
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.GO_LOAD_QUEUE_POINT,
                DispatchUnitStateEnum.GO_LOAD_QUEUE_POINT,
                DispatchUnitStateEnum.EXEC_UNLOAD);
        Point endPoint = unitInput.getUnit().getLoadQueuePoint();
        if (changeFlag && null != endPoint) {
            log.debug("【车辆[{}]前往装载区排队点,point={}】", getVehicleId(), endPoint.toString());
            setTaskType(TaskTypeEnum.LOAD);
            setDispatchState(DispatchStateEnum.NOLOAD_RUN);
            setTaskSpot(unitInput.getUnit().getLoadQueueId());
            unitInput.setArriveHandler(this::loadWaitIntoCommand);
            if (isNeedCreatePath(endPoint)) {
                unitInput.setEndPoint(endPoint);
                unitInput.createPath(0);
            } else {
                unitInput.startRun();
            }
        }
    }

    /**
     * 装载区等待进车信号
     */
    private void loadWaitIntoCommand() {
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.LOAD_WAIT_INTO_COMMAND,
                DispatchUnitStateEnum.LOAD_WAIT_INTO_COMMAND,
                DispatchUnitStateEnum.GO_LOAD_QUEUE_POINT);
        if (changeFlag) {
            log.debug("【车辆[{}]等待进车信号】", getVehicleId());
            //判断装载区状态
            LoadArea loadArea = unitInput.getUnit().getLoadArea();
            if (null != loadArea && LoadAreaStateEnum.READY.equals(loadArea.getStatus())) {
                //可以进车直接去装载点
                goLoadTaskPoint();
                return;
            }
            this.curWaitCommand = NotifyCommandEnum.EXCAVATOR_INOTSIGN_COMMAND;
            loadAreaSign();
        }
    }

    /**
     * 装载区自动信号
     */
    private void loadAreaSign() {
        LoadArea loadArea = unitInput.getUnit().getLoadArea();
        if (null != loadArea) {
            if (LoadAreaStateEnum.OFFLINE.equals(loadArea.getStatus())) {
                loadArea.taskSpotStart();
            }
            switch (this.curWaitCommand) {
                case EXCAVATOR_INOTSIGN_COMMAND:
                    loadArea.loadAreaEntry();
                    break;
                case EXCAVATOR_OUTSIGN_COMMAND:
                    loadArea.loadAreaWorkDone();
                    BaseUtil.timer(loadArea::loadAreaEntry, 3000);
                    break;
                case EXCAVATOR_BEGIN_LOAD_COMMAND:
                    loadArea.loadAreaWorkBegin();
                    break;
            }
        }
    }

    /**
     * 收到进车信号
     */
    private void receiveIntoCommand() {
        log.debug("【车辆[{}]收到进车信号】", getVehicleId());
        //tcl DispatchStateEnum 改为装载工作
        this.curWaitCommand = NotifyCommandEnum.NONE_COMMAND;
        goLoadTaskPoint();
    }

    /**
     * 前往装载点
     */
    private void goLoadTaskPoint() {
        changeLoadAreaState(LoadAreaStateEnum.RELEVANCE);
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.GO_LOAD_TASK_POINT,
                DispatchUnitStateEnum.GO_LOAD_TASK_POINT,
                DispatchUnitStateEnum.LOAD_WAIT_INTO_COMMAND);
        Point endPoint = unitInput.getUnit().getLoadPoint();
        if (changeFlag && null != endPoint) {
            log.debug("【车辆[{}]前往装载点,point={}】", getVehicleId(), endPoint.toString());
            setTaskSpot(unitInput.getUnit().getLoadPointId());
            unitInput.setArriveHandler(this::prepareLoad);
            if (isNeedCreatePath(endPoint)) {
                unitInput.setEndPoint(endPoint);
                unitInput.createPath(0);
            } else {
                unitInput.startRun();
            }
        }
    }

    /**
     * 准备装载,等待装车信号
     */
    private void prepareLoad() {
        changeLoadAreaState(LoadAreaStateEnum.PREPARE);
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.PREPARE_LOAD,
                DispatchUnitStateEnum.PREPARE_LOAD,
                DispatchUnitStateEnum.GO_LOAD_TASK_POINT);
        if (changeFlag) {
            log.debug("【车辆[{}]等待装车信号】", getVehicleId());
            this.curWaitCommand = NotifyCommandEnum.EXCAVATOR_BEGIN_LOAD_COMMAND;
            loadAreaSign();
        }
    }

    /**
     * 收到装车信号
     */
    private void receivePrepareLoadCommand() {
        changeLoadAreaState(LoadAreaStateEnum.WORKING);
        log.debug("【车辆[{}]收到装车信号】", getVehicleId());
        this.curWaitCommand = NotifyCommandEnum.NONE_COMMAND;
        setDispatchState(DispatchStateEnum.LOAD_WORKING);
        //发送装车命令到车载，等待车载命令后执行装载
        CommSend.vehAutoLoad(vehicleId);
        DelayedService.Task task = DelayedService.buildTask();
        task.withTask(() -> {
            if (unitInput.getVehicleTask().getHelper().getTaskCodeCommand().isContains(10, TaskCodeEnum.TASKLOAD)) {
                log.warn("【{}】收到装载反馈，开始执行装载", vehicleId);
                execLoad();
                task.withExec(false);
            }
        })
                .withNum(20)
                .withDelay(500)
                .withTaskId(TimerCommand.getTemporaryKey(vehicleId,"receivePrepareLoadCommand"))
                .withAfterTask(() -> {
                    if (task.getNum() == 0) {
                        log.error("【{}】下发装载指令，没有得到车载反馈", vehicleId);
                        execLoad();
                    }
                })
                .withPrintLog(false)
                .withDesc("等待装载指令反馈");
        DelayedService.addTask(task);
    }

    /**
     * 执行装载,等待出车信号
     */
    private void execLoad() {
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.EXEC_LOAD,
                DispatchUnitStateEnum.EXEC_LOAD,
                DispatchUnitStateEnum.PREPARE_LOAD);
        if (changeFlag) {
            log.debug("【车辆[{}]执行装载...】", getVehicleId());
            this.curWaitCommand = NotifyCommandEnum.EXCAVATOR_OUTSIGN_COMMAND;
            //tcl 测试代码,有电铲时删除
            BaseUtil.timer(this::loadAreaSign, 5000);
        }
    }

    /**
     * 收到出车信号,先转为待机等待，在生成路径
     */
    private void receiveOutCommand() {
        log.debug("【车辆[{}]收到出车信号】", getVehicleId());
        setDispatchState(DispatchStateEnum.LOAD_RUN);
        goUnLoadQueuePoint();
    }


    /********************************** 卸载 ****************************************/

    /**
     * 去卸载排队点
     */
    private void goUnLoadQueuePoint() {
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.GO_UNLOAD_QUEUE_POINT,
                DispatchUnitStateEnum.GO_UNLOAD_QUEUE_POINT,
                DispatchUnitStateEnum.EXEC_LOAD);
        Point endPoint = unitInput.getUnit().getUnloadQueuePoint();
        if (changeFlag && null != endPoint) {
            log.debug("【车辆[{}]去卸载排队点,point={}】", getVehicleId(), endPoint.toString());
            setTaskType(TaskTypeEnum.UNLOAD);
            setDispatchState(DispatchStateEnum.LOAD_RUN);
            setTaskSpot(unitInput.getUnit().getUnloadQueueId());
            unitInput.setArriveHandler(this::unloadWaitIntoCommand);
            if (isNeedCreatePath(endPoint)) {
                unitInput.setEndPoint(endPoint);
                unitInput.createPath(0);
            } else {
                unitInput.startRun();
            }
        }
    }

    /**
     * 卸载区等待允许进车信号
     */
    private void unloadWaitIntoCommand() {
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.UNLOAD_WAIT_INTO_COMMAND,
                DispatchUnitStateEnum.UNLOAD_WAIT_INTO_COMMAND,
                DispatchUnitStateEnum.GO_UNLOAD_QUEUE_POINT);
        if (changeFlag) {
            log.debug("【车辆[{}]卸载区等待允许进车信号】", getVehicleId());
            //判断卸载区状态
            UnloadArea unloadArea = unitInput.getUnit().getUnloadArea();
            if (null != unloadArea && UnLoadAreaStateEnum.ON.equals(unloadArea.getState())) {
                this.curWaitCommand = NotifyCommandEnum.NONE_COMMAND;
                //可以进车直接去卸载点
                goUnloadTaskPoint();
                return;
            }
            this.curWaitCommand = NotifyCommandEnum.UNLOAD_AREA_STATE_NO;
        }
    }

    /**
     * 前往卸载点
     */
    private void goUnloadTaskPoint() {
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.GO_UNLOAD_TASK_POINT,
                DispatchUnitStateEnum.GO_UNLOAD_TASK_POINT,
                DispatchUnitStateEnum.UNLOAD_WAIT_INTO_COMMAND);
        Point endPoint = unitInput.getUnit().getUnloadPoint();
        if (changeFlag && null != endPoint) {
            log.debug("【车辆[{}]前往卸载点,point={}】", getVehicleId(), endPoint.toString());
            setTaskSpot(unitInput.getUnit().getUnloadId());
            unitInput.setArriveHandler(this::prepareUnload);
            if (isNeedCreatePath(endPoint)) {
                unitInput.setEndPoint(endPoint);
                unitInput.createPath(0);
            } else {
                unitInput.startRun();
            }
        }
    }

    /**
     * 准备卸载
     */
    private void prepareUnload() {
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.PREPARE_UNLOAD,
                DispatchUnitStateEnum.PREPARE_UNLOAD
                , DispatchUnitStateEnum.GO_UNLOAD_TASK_POINT);
        if (changeFlag) {
            log.debug("【车辆[{}]准备卸载】", getVehicleId());
            //给车载发送卸载命令
            this.curWaitCommand = NotifyCommandEnum.VEHICLE_UNLOAD_START_COMMAND;
            CommSend.vehAutoUnload(vehicleId);
        }
    }

    /**
     * 执行卸载
     */
    private void execUnload() {
        boolean changeFlag = unitInput.changeTaskState(DispatchUnitStateEnum.EXEC_UNLOAD,
                DispatchUnitStateEnum.EXEC_UNLOAD,
                DispatchUnitStateEnum.PREPARE_UNLOAD);
        if (changeFlag) {
            setDispatchState(DispatchStateEnum.UNLOAD_WORKING);
            log.debug("【车辆[{}]执行卸载...】", getVehicleId());
            this.setCurWaitCommand(NotifyCommandEnum.VEHICLE_UNLOAD_END_COMMAND);
        }
    }

    /**
     * 收到卸载开始指令
     */
    private void receiveUnloadStartCommand() {
        log.debug("【车辆[{}]收到卸载开始指令】", getVehicleId());
        //收到卸矿指令删除定时器
        BaseUtil.cancelDelayTask(TimerCommand.VEHICLE_AUTO_UNLOAD_COMMAND + vehicleId);
        execUnload();
    }

    /**
     * 收到卸载完成指令
     */
    private void receiveUnloadEndCommand() {
        log.debug("【车辆[{}]收到卸载完成指令】", getVehicleId());
        setDispatchState(DispatchStateEnum.NOLOAD_RUN);
        //只有待机后才能执行下个命令
        DelayedService.Task task = DelayedService.buildTask();
        task.withTask(() -> {
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            if (null != vehicleTask && DispatchUnitStateEnum.EXEC_UNLOAD.equals(unitInput.getCurState())) {
                if (vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKSTANDBY)) {
                    task.withExec(false);
                    runNextCycle();
                } else {
                    task.withExec(false); //不是调度任务，直接退出
                }
            }
        }).withTaskId(TimerCommand.getTemporaryKey(vehicleId,"receiveUnloadEndCommand"))
                .withDelay(500)
                .withAtOnce(true)
                .withNum(400);
        DelayedService.addTask(task);
    }

    /**
     * 开始下个循环
     */
    private void runNextCycle() {
        //通知调度单元完成一次循环任务
        boolean finishOneTask = unitInput.getUnit().vehicleFinishOneTask(vehicleId);
        if (!finishOneTask) {
            log.debug("【车辆[{}]开始下个循环】", getVehicleId());
            goLoadQueuePoint();
        }
    }

    /**
     * 修改装载区状态
     */
    private void changeLoadAreaState(LoadAreaStateEnum state) {
        Unit unit = unitInput.getUnit();
        if (null != unit && null != state) {
            LoadArea loadArea = unit.getLoadArea();
            if (null != loadArea) {
                if (LoadAreaStateEnum.RELEVANCE.equals(state)) {
                    loadArea.setRelevance(vehicleId);
                }
                loadArea.setAreaState(state);
            }
        }
    }

    /**
     * 设置任务点信息
     */
    private void setTaskSpot(Integer taskSpotId) {
        VehicleTaskHelper helper = unitInput.getVehicleTask().getHelper();
        helper.setTaskSpot(taskSpotId);
    }

    /**
     * 设置任务状态
     */
    private void setTaskType(TaskTypeEnum taskType) {
        VehicleTaskHelper helper = unitInput.getVehicleTask().getHelper();
        helper.getTaskStateManager().setCurTaskType(taskType);
    }

    /**
     * 设置调度状态
     */
    private void setDispatchState(DispatchStateEnum state) {
        if (null != state) {
            unitInput.getVehicleTask().getHelper().getDispatchStateManager().changeDispatchState(state);
        }
    }

    /**
     * 判断是否需要创建路径,false为不需要重新生成
     */
    private boolean isNeedCreatePath(Point endPoint) {
        PathManager manager = unitInput.getVehicleTask().getHelper().getPathManager();
        Point point = InputCache.getEndPoint(vehicleId);
        if (manager.isValState(PathStateEnum.PATH_CREATED)) {
            if (null != point && point.equals(endPoint)) {
                return false;
            }
        }
        return true;
    }


    /**
     * 车辆、挖掘机、卸点状态改变
     */
    @Override
    public void stateChange(EventType type, Object value) {
        NotifyCommandEnum command = (NotifyCommandEnum) value;
        log.debug("监听数据值,eventType={},value={}", type, command);
        if (null == this.curWaitCommand || !this.curWaitCommand.equals(command)) {
            log.debug("车辆[{}]接收到异常通知[{}],waitNotify=[{}]", vehicleId, command, this.curWaitCommand);
            return;
        }
        switch (command) {
            case EXCAVATOR_INOTSIGN_COMMAND:
                receiveIntoCommand();
                break;
            case EXCAVATOR_BEGIN_LOAD_COMMAND:
                receivePrepareLoadCommand();
                break;
            case EXCAVATOR_OUTSIGN_COMMAND:
                receiveOutCommand();
                break;
            case VEHICLE_UNLOAD_START_COMMAND://开始卸载
                receiveUnloadStartCommand();
                break;
            case VEHICLE_UNLOAD_END_COMMAND://卸载完成
                receiveUnloadEndCommand();
                break;
        }
    }
}
