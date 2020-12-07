package com.baseboot.entry.dispatch.area;

import com.baseboot.common.service.DelayedService;
import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorTask;
import com.baseboot.entry.dispatch.monitor.vehicle.Unit;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.*;
import com.baseboot.entry.map.Point;
import com.baseboot.entry.map.SemiStatic;
import com.baseboot.enums.*;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.interfaces.send.MapSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.dispatch.input.*;
import com.baseboot.service.dispatch.vehicle.commandHandle.TaskCodeCommand;
import com.baseboot.service.dispatch.task.TriggerTask;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Data
@Slf4j
public class LoadArea extends AbstractEventPublisher implements TaskArea {

    private Integer loadAreaId;

    private QueuePoint queuePoint;

    private LoadPoint loadPoint;

    private LoadAreaStateEnum status = LoadAreaStateEnum.OFFLINE;//装载区状态

    private AreaTypeEnum loadType = AreaTypeEnum.LOAD_AREA;

    private Integer vehicleId;//关联车编号

    private volatile long preCommandTime = 0;


    /**
     * 获取在当前装载区内的电铲
     */
    public ExcavatorTask getExcavator() {
        Map<Integer, ExcavatorTask> excavatorMap = BaseCacheUtil.getExcavatorMap();
        for (ExcavatorTask excavatorTask : excavatorMap.values()) {
            LoadArea loadArea = excavatorTask.getLoadArea();
            if (null != loadArea && loadArea.getLoadAreaId().equals(loadAreaId)) {
                return excavatorTask;
            }
        }
        return null;
    }

    @Override
    public Integer getTaskAreaId() {
        return loadAreaId;
    }

    @Override
    public AreaTypeEnum getAreaType() {
        return loadType;
    }

    public void setAreaState(LoadAreaStateEnum state) {
        if (null != state) {
            log.debug("装载区状态改变:areaId={},state={}", loadAreaId, state.getDesc());
            preCommandTime = BaseUtil.getCurTime();
            this.status = state;
            updateCache();

            if (!LoadAreaStateEnum.RELEVANCE.equals(state) &&
                    !LoadAreaStateEnum.PREPARE.equals(state) &&
                    !LoadAreaStateEnum.WORKING.equals(state)) {
                setRelevance(null);
            }
        }
    }

    /**
     * 设置关联车编号
     */
    public void setRelevance(Integer vehicleId) {
        if (null != vehicleId) {
            log.debug("【{}】装载区设置关联车辆编号[{}]", loadAreaId, vehicleId);
        }else{
            log.debug("【{}】装载区取消关联车[{}]", loadAreaId, vehicleId);
        }
        this.vehicleId = vehicleId;
    }


    /**
     * 挖掘机所在装载区状态改变,发布事件
     */
    @Override
    public void eventPublisher(EventType eventType, Object value) {
        for (Listener listener : getListeners()) {
            listener.stateChange(EventType.EXCAVATOR, value);
        }
    }

    /**
     * 跟新缓存中任务区状态
     */
    @Override
    public void updateCache() {
        Map<String, Object> params = new HashMap<>();
        if (null == loadAreaId) {
            return;
        }
        params.put("id", loadAreaId);
        params.put("taskType", TaskTypeEnum.LOAD.getValue());
        List<Map<String, Object>> taskSpots = new ArrayList<>();
        if (null != loadPoint) {
            Map<String, Object> taskSpot = new HashMap<>();
            taskSpot.put("id", loadPoint.getLoadId());
            taskSpot.put("state", status.getValue());
            taskSpots.add(taskSpot);
        }
        params.put("taskSpots", taskSpots);
        RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.DISPATCH_TASK_AREA_PREFIX + loadAreaId, BaseUtil.toJson(params));
    }

    /***************************信号***************************/

    /**
     * 当前状态校验
     */
    private boolean checkCurState(LoadAreaStateEnum... states) {
        synchronized (BaseCacheUtil.objectLock("checkCurState-" + loadAreaId)) {
            cancelRelevance(vehicleId);
            boolean contains = Arrays.asList(states).contains(status);
            if (!contains) {
                log.error("装载区【{}】前置状态校验失败!,curState={},targetState={}", loadAreaId, status, Arrays.asList(states).toString());
            }
            return contains;
        }
    }

    /**
     * 如果绑定的车辆取消了调度任务，则取消关联关系
     */
    public void cancelRelevance(Integer targetVehicleId) {
        if (null != targetVehicleId) {
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(targetVehicleId);
            if (!vehicleTask.getHelper().getInputStateManager().isGetInputType(DispatchUnitInput.class)) {
                setRelevance(null);
                if (LoadAreaStateEnum.RELEVANCE.equals(status) ||
                        LoadAreaStateEnum.WORKING.equals(status) ||
                        LoadAreaStateEnum.PREPARE.equals(status)) {
                    log.debug("【{}】关联状态转为允许进车状态", loadAreaId);
                    setAreaState(LoadAreaStateEnum.READY);//关联状态转为准许进车状态
                }
            }
        }
    }

    /**
     * 重新设置装载点,定时任务获取
     */
    public void loadPointReset() {
        String key = TimerCommand.VEHICLE_TEMPORARY_TASK_PREFIX + "getDipTaskAngle_" + loadAreaId;
        ExcavatorTask excavator = getExcavator();
        if (null != excavator && ConnectionStateEnum.CONN.equals(excavator.getMonitorState())) {
            Point point = addDistance(1, excavator.getCurLocation(), excavator.getLoadLocation());
            MapSend.getDipTaskAngle(excavator.getCurLocation(), point, loadAreaId);
            //添加任务,5秒没得到反馈则直接执行
            DelayedService.addTaskNoExist(() -> {
                log.warn("【{}】装载区没有电铲，使用原装载点", loadAreaId);
                dynamicLocation(null);//没有电铲，使用原装载点
            }, 5000, key, false).withNum(1);
        } else {
            dynamicLocation(null);//没有电铲，使用原装载点
        }
    }

    /**
     * 动态设置装载点
     */
    public void dynamicLocation(Point loadPoint) {
        if (null != loadPoint) {
            this.loadPoint.setLoadPoint(loadPoint);
        }

        //查找哪些车在等待进车信号
        VehicleTask task = getVehicleByLoad(this.getLoadAreaId());
        if (null == task) {
            //如果没有等待进车信号的车
            this.eventPublisher(null, NotifyCommandEnum.EXCAVATOR_INOTSIGN_COMMAND);
        } else {
            //有车在等待进车信号，改变任务，改为去装载点
            DispatchInput input = task.getHelper().getInputStateManager().getCurInputType();
            if (input instanceof DispatchUnitInput) {
                ((DispatchUnitInput) input).goLoadPointTask();
            }
        }
    }

    /**
     * 铲斗垂直距离
     */
    private Point addDistance(double dis, Point disPoint, Point taskPoint) {
        double x = Math.cos(disPoint.getYawAngle() / 180 * Math.PI) * dis + taskPoint.getX();
        double y = Math.sin(disPoint.getYawAngle() / 180 * Math.PI) * dis + taskPoint.getY();
        return new Point(x, y, taskPoint.getZ(), taskPoint.getYawAngle());
    }

    /**
     * 进车信号
     */
    public boolean loadAreaEntry() {
        if (!checkCurState(LoadAreaStateEnum.DELAY)) {
            return false;
        }
        String format = BaseUtil.format("【{}】装载区进车", loadAreaId);
        LogUtil.addLogToRedis(LogType.WARN, "monitor-loadArea-" + loadAreaId, format);
        this.setAreaState(LoadAreaStateEnum.READY);
        loadPointReset();
        return true;
    }

    /**
     * 出车信号
     */
    public boolean loadAreaWorkDone() {
        if (!checkCurState(LoadAreaStateEnum.WORKING)) {
            return false;
        }

        /*if (BaseUtil.getCurTime() - preCommandTime < 4 * 1000) {
            log.warn("【{}】装车指令和出车指令反应时间不够4秒", loadAreaId);
            return false;
        }*/

        if(null==vehicleId){
            log.error("【{}】装载区没有关联车，不能接收【出车信号】", loadAreaId);
            return false;
        }
        //判断关联的调度状态
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null == vehicleTask) {
            log.error("【{}】装载区没有关联车，不能接收【出车信号】", loadAreaId);
            return false;
        }

        //关联车不是等待出车状态
        if (!vehicleTask.getHelper().getVehicleStateManager().getTaskStateManager().isInputTaskState(DispatchUnitInput.class, DispatchUnitStateEnum.EXEC_LOAD.getValue())) {
            String format = BaseUtil.format("【{}】装载区的关联车{}不是等待出车状态，不能接收[出车信号]", loadAreaId, vehicleId);
            LogUtil.addLogToRedis(LogType.ERROR, "monitor-loadArea-" + loadAreaId, format);
            log.error(format);
            return false;
        }
        ;
        String format = BaseUtil.format("【{}】装载区出车", loadAreaId);
        LogUtil.addLogToRedis(LogType.WARN, "monitor-loadArea-" + loadAreaId, format);
        this.setAreaState(LoadAreaStateEnum.DELAY);
        this.eventPublisher(null, NotifyCommandEnum.EXCAVATOR_OUTSIGN_COMMAND);
        return true;
    }

    /**
     * 查找哪些车在等待进车信号
     */
    private VehicleTask getVehicleByLoad(Integer loadId) {
        Collection<Unit> units = BaseCacheUtil.getUnits();
        VehicleTask target = null;
        for (Unit unit : units) {
            LoadArea loadArea = unit.getLoadArea();
            if (null != loadArea) {
                LoadPoint loadPoint = loadArea.getLoadPoint();
                if (null != loadPoint) {
                    Point point = loadPoint.getLoadPoint();
                    //不是当前装载区的调度单元
                    if (!unit.getLoadArea().getLoadAreaId().equals(loadId)) {
                        continue;
                    }
                    Set<VehicleTask> vehicleTasks = unit.getVehicleTasks();
                    double distance = 1000000;
                    for (VehicleTask vehicleTask : vehicleTasks) {
                        DispatchInput input = InputCache.getDispatchInput(vehicleTask.getVehicleId());
                        if (input instanceof DispatchUnitInput) {
                            if (DispatchUnitStateEnum.LOAD_WAIT_INTO_COMMAND
                                    .equals(input.getTaskState())) {//如果是等待进车信号
                                double dis = DispatchUtil.twoPointDistance(vehicleTask.getCurLocation(), point);
                                if (distance > dis) {
                                    distance = dis;
                                    target = vehicleTask;
                                }
                            }
                        }
                    }
                }
            }
        }
        return target;
    }


    /**
     * 任务点开装
     */
    public boolean loadAreaWorkBegin() {
        if (!checkCurState(LoadAreaStateEnum.PREPARE)) {
            return false;
        }
        Collection<VehicleTask> vehicleTasks = BaseCacheUtil.getVehicleTasks();
        boolean flag = false;
        for (VehicleTask task : vehicleTasks) {
            String taskState = task.getHelper().getTaskStateManager().getTaskState();
            DispatchUnitStateEnum prepareLoad = DispatchUnitStateEnum.PREPARE_LOAD;
            //当前装载区有车在等待装载信号
            SemiStatic curArea = task.getHelper().getCurArea();
            if (null != curArea && curArea.getId().equals(this.getTaskAreaId()) && prepareLoad.getValue().equals(taskState)) {
                flag = true;
                break;
            }
        }
        if (flag) {
            this.eventPublisher(null, NotifyCommandEnum.EXCAVATOR_BEGIN_LOAD_COMMAND);
            String format = BaseUtil.format("【{}】装载区开装,关联装载车编号[{}]", loadAreaId, vehicleId);
            LogUtil.addLogToRedis(LogType.WARN, "monitor-loadArea-" + loadAreaId, format);
            return true;
        }
        return false;
    }

    /**
     * 取消进车
     */
    public boolean loadAreaEntryCancel() {
        if (!checkCurState(LoadAreaStateEnum.READY, LoadAreaStateEnum.RELEVANCE)) {
            return false;
        }
        if (null != vehicleId) {
            String format = BaseUtil.format("【{}】装载区取消进车，停止关联车辆[{}]运行", loadAreaId, vehicleId);
            log.warn(format);
            LogUtil.addLogToRedis(LogType.WARN, "monitor-loadArea-" + loadAreaId, format);
        }
        this.setAreaState(LoadAreaStateEnum.DELAY);
        return judgeTaskTypeByLoad(this.getLoadAreaId());
    }


    /**
     * 查找装载区对应的调度单元，并判断其任务类型,为去装载点则停止运行
     */
    private boolean judgeTaskTypeByLoad(Integer loadId) {
        Collection<Unit> units = BaseCacheUtil.getUnits();
        for (Unit unit : units) {
            LoadArea loadArea = unit.getLoadArea();
            if (null != loadArea) {
                //不是当前装载区的调度单元
                if (!unit.getLoadArea().getLoadAreaId().equals(loadId)) {
                    continue;
                }
            }
            Set<VehicleTask> vehicleTasks = unit.getVehicleTasks();
            for (VehicleTask vehicleTask : vehicleTasks) {
                DispatchInput input = InputCache.getDispatchInput(vehicleTask.getVehicleId());
                if (input instanceof DispatchUnitInput) {
                    DispatchUnitInput unitInput = (DispatchUnitInput) input;
                    if (DispatchUnitStateEnum.GO_LOAD_TASK_POINT
                            .equals(unitInput.getTaskState())) {//如果去装载点
                        //安全停车
                        String format = BaseUtil.format("【{}】因取消进车信号，导致停止运行，下发安全停车", vehicleTask.getVehicleId());
                        log.warn(format);
                        LogUtil.addLogToRedis(LogType.WARN, "dispatch-" + vehicleTask.getVehicleId(), format);
                        CommSend.vehAutoSafeParking(vehicleTask.getVehicleId());
                        //改变任务，改为等待进车信号
                        TriggerTask triggerTask = new TriggerTask("loadAreaEntryCancel_" + vehicleTask.getVehicleId(), 5000, () -> {
                            if (vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKSTANDBY) ||
                                    vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKNORMALPARKBYTRAJECTORY)) {
                                if (vehicleTask.getHelper().getVehicleStateManager().isFreeState()) {
                                    vehicleTask.getHelper().getPathManager().initInput(unitInput);
                                    unitInput.setCurState(DispatchUnitStateEnum.LOAD_WAIT_INTO_COMMAND);
                                    unitInput.startTask(vehicleTask.getVehicleId(), new Response());
                                    return true;
                                }
                            }
                            return false;
                        });
                        TaskCodeCommand.addTask(vehicleTask.getVehicleId(), triggerTask);
                    }
                }
            }
        }
        return true;
    }

    /**
     * 任务点开工
     */
    public void taskSpotStart() {
        if (!checkCurState(LoadAreaStateEnum.OFFLINE)) {
            return;
        }
        String format = BaseUtil.format("【{}】装载区开工", loadAreaId);
        LogUtil.addLogToRedis(LogType.WARN, "monitor-loadArea-" + loadAreaId, format);
        this.setAreaState(LoadAreaStateEnum.DELAY);
    }

    /**
     * 任务点停工
     */
    public void taskSpotStop() {
        if (checkCurState(LoadAreaStateEnum.RELEVANCE, LoadAreaStateEnum.PREPARE, LoadAreaStateEnum.WORKING)) {
            log.error("【{}】装载区当前状态为准备或工作状态，不能停工!!！", loadAreaId);
            return;
        }
        String format = BaseUtil.format("【{}】装载区停工", loadAreaId);
        LogUtil.addLogToRedis(LogType.WARN, "monitor-loadArea-" + loadAreaId, format);
        this.setAreaState(LoadAreaStateEnum.OFFLINE);
    }


    @Override
    public String toString() {
        return loadType.getDesc() + ":areaId=" + loadAreaId + ",queuePoint=[" + (null == queuePoint ? "" : queuePoint.toString()) + "],loadPoint=[" + (null == loadPoint ? "" : loadPoint.toString()) + "]";
    }
}
