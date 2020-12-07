package com.baseboot.service.dispatch.input;

import com.baseboot.common.service.DelayedService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.monitor.vehicle.Unit;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.IEnum;
import com.baseboot.entry.global.Response;
import com.baseboot.entry.map.SemiStatic;
import com.baseboot.enums.AreaTypeEnum;
import com.baseboot.enums.DispatchStateEnum;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.manager.PathManager;
import com.baseboot.service.dispatch.manager.UnitTaskStateManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * 调度单元输入
 */
@Slf4j
@Data
public class DispatchUnitInput extends DispatchInputAdapter<DispatchUnitStateEnum> {

    private Unit unit;

    private VehicleTask vehicleTask;

    private UnitTaskStateManager unitTaskStateManager;

    private DispatchUnitStateEnum curState;

    private final static String EXCAVATOR_LISTENER_PREFIX = "excavatorTask-";

    private final static String VEHICLE_LISTENER_PREFIX = "vehicle-";

    /**
     * 车辆到达后的处理
     */
    private Runnable arriveHandler;

    private LinkedBlockingDeque<DispatchUnitStateEnum> taskQueue = new LinkedBlockingDeque<>();


    /**
     * 调度循环任务
     * */ {
        taskQueue.add(DispatchUnitStateEnum.GO_LOAD_QUEUE_POINT);
        taskQueue.add(DispatchUnitStateEnum.LOAD_WAIT_INTO_COMMAND);
        taskQueue.add(DispatchUnitStateEnum.GO_LOAD_TASK_POINT);
        taskQueue.add(DispatchUnitStateEnum.PREPARE_LOAD);
        taskQueue.add(DispatchUnitStateEnum.EXEC_LOAD);
        taskQueue.add(DispatchUnitStateEnum.GO_UNLOAD_QUEUE_POINT);
        taskQueue.add(DispatchUnitStateEnum.UNLOAD_WAIT_INTO_COMMAND);
        taskQueue.add(DispatchUnitStateEnum.GO_UNLOAD_TASK_POINT);
        taskQueue.add(DispatchUnitStateEnum.PREPARE_UNLOAD);
        taskQueue.add(DispatchUnitStateEnum.EXEC_UNLOAD);
    }


    @Override
    public void setPathManager(Integer vehicleId, PathManager pathManager) {
        super.setPathManager(vehicleId, pathManager);
        unitTaskStateManager = new UnitTaskStateManager(getVehicleId(), this);
        this.vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        Integer unitId = vehicleTask.getHelper().getUnitId();
        this.unit = BaseCacheUtil.getUnit(unitId);
        //装载区添加监听器、车辆状态监听器
        if (null != this.unit) {
            unit.getLoadArea().addListener(EXCAVATOR_LISTENER_PREFIX + vehicleId, unitTaskStateManager);
            this.vehicleTask.getHelper().getTaskCodeCommand().addListener(VEHICLE_LISTENER_PREFIX + vehicleId, unitTaskStateManager);//设置监听
        }
    }

    /**
     * 开始运行调度任务
     */
    public boolean startTask(Integer vehicleId, Response response) {
        if (null == this.unit) {
            log.error("车辆[{}]没有分配调度单元，不能运行调度任务!", vehicleId);
            response.withFailMessage("当前车辆没有分配调度单元，不能运行调度任务!");
            return false;
        }
        if (unit.isFinished()) {
            log.debug("车辆[{}]当前调度任务已完成!", getVehicleId());
            response.withFailMessage("当前调度任务已完成!");
            return false;
        }

        if (!this.vehicleTask.getHelper().getRunStateManager().isStart()) {
            DispatchUnitStateEnum startTask = getStartTask();
            if (null != startTask) {
                changeTaskState(startTask);
                unitTaskStateManager.changeUnitState(startTask);
                response.withSucMessage("启动调度任务成功");
                return true;
            } else {
                log.error("车辆【{}】不是空闲状态", vehicleId);
            }
        }

        log.error("车辆[{}]正在运行，不能执行调度任务!", getVehicleId());
        response.withFailMessage("车辆正在运行状态!");
        return false;
    }

    /**
     * 收到进车信号,触发进车
     */
    public void goLoadPointTask() {
        if (DispatchUnitStateEnum.LOAD_WAIT_INTO_COMMAND.equals(this.curState)) {
            this.curState = DispatchUnitStateEnum.GO_LOAD_TASK_POINT;
            unitTaskStateManager.changeUnitState(curState);
        }
    }


    /**
     * 强制设置当前任务,不能直接运行
     */
    public void setCurTask(DispatchUnitStateEnum curState) {
        DispatchUnitStateEnum state = taskQueue.getFirst();
        while (null != state && !state.equals(curState) && taskQueue.contains(curState)) {
            state = taskQueue.poll();
            taskQueue.addLast(state);
            state = taskQueue.getFirst();
        }
        log.debug("【{}】车辆设置当前任务[{}]", getVehicleId(), curState.getDesc());
    }

    /**
     * 获取当前任务
     */
    public DispatchUnitStateEnum getCurTask() {
        return taskQueue.getFirst();
    }

    /**
     * 获取前一个任务
     */
    public DispatchUnitStateEnum getPreTask() {
        return taskQueue.getLast();
    }


    @Override
    public boolean createPath(int planType) {
        return super.createPath(getPlanType());
    }

    /**
     * 获取规划类型
     *
     * @return
     * 0：正常规划，没有特殊限制；
     * 1：不能倒车起步；
     * 2：必须倒车进入终点，且前进后退只切换一次；
     * 3：必须前进到达终点，中间不退；
     * 5:进入装载点；
     * 6：反向规划
     */
    private int getPlanType() {
        DispatchUnitStateEnum curTask = getCurTask();
        if (DispatchUnitStateEnum.GO_LOAD_TASK_POINT.equals(curTask)) {
            return 5;
        }

        if (DispatchUnitStateEnum.GO_UNLOAD_TASK_POINT.equals(curTask)) {
            return 2;
        }

        if (DispatchUnitStateEnum.GO_LOAD_QUEUE_POINT.equals(curTask) ||
                DispatchUnitStateEnum.GO_UNLOAD_QUEUE_POINT.equals(curTask)) {
            return 0;
        }

        DispatchUnitStateEnum preTask = getPreTask();
        if (DispatchUnitStateEnum.EXEC_LOAD.equals(preTask) ||
                DispatchUnitStateEnum.EXEC_UNLOAD.equals(preTask)) {
            return 1;
        }
        return 0;
    }

    /**
     * 调度单元车辆停止
     */
    @Override
    public void stopRun() {
        super.stopRun();
        //删除监听器
        unit.getLoadArea().removeListener(unitTaskStateManager);
        this.vehicleTask.getHelper().getTaskCodeCommand().removeListener(unitTaskStateManager);
        //修改调度状态
        if (vehicleTask.getHelper().getDispatchStateManager().isDispatchNoLoadState()) {
            vehicleTask.getHelper().getDispatchStateManager().changeDispatchState(DispatchStateEnum.NOLOAD_FREE);
        } else {
            vehicleTask.getHelper().getDispatchStateManager().changeDispatchState(DispatchStateEnum.LOAD_FREE);
        }
    }


    /**
     * 调度单元车辆开始创建路径通知
     */
    @Override
    public void startCreatePathNotify() {
        super.startCreatePathNotify();
    }

    /**
     * 调度单元车辆路径创建成功通知
     * 路径生成成功后，启动车辆
     */
    @Override
    public void createPathSuccessNotify() {
        super.createPathSuccessNotify();
        boolean receiveObstacle = vehicleTask.getHelper().getVehicleMonitorManager().isReceiveObstacle();
        //收到障碍物不能直接运行
        if (receiveObstacle) {
            return;
        }
        super.startRun();
    }

    /**
     * 调度单元车辆路径创建异常通知，转为空闲
     * 路径生成失败,定时请求路径
     */
    @Override
    public void createPathErrorNotify() {
        super.createPathErrorNotify();
        DelayedService.addTask(() -> {
            this.createPath(0);
        }, 5000);
    }

    @Override
    public void runErrorNotify() {
        super.runErrorNotify();
    }

    /**
     * 调度单元车辆开始通知
     */
    @Override
    public void startRunNotify() {
        super.startRunNotify();
    }

    /**
     * 调度单元车辆停止通知
     */
    @Override
    public void stopRunNotify() {
        super.stopRunNotify();
    }

    /**
     * 调度单元车辆到达通知，改为下个任务状态
     */
    @Override
    public void arriveNotify() {
        super.arriveNotify();
        if (null != arriveHandler) {
            log.debug("车辆[{}]运行到达处理逻辑", getVehicleId());
            Runnable handler = this.arriveHandler;
            this.arriveHandler.run();
            if (handler.equals(this.arriveHandler)) {
                this.arriveHandler = null;
            }
        }
    }

    /**
     * 判断运行调度时要执行的任务
     */
    private DispatchUnitStateEnum getStartTask() {
        if (null != vehicleTask) {
            if (judgeCurTask()) {
                return curState;
            }
            DispatchUnitStateEnum state = getTaskCodeEnum(vehicleTask.getHelper().getTaskStateManager().getTaskState());
            if (null != state && state.equals(DispatchUnitStateEnum.FREE)) {
                if (vehicleTask.getHelper().getDispatchStateManager().isDispatchNoLoadState()) {
                    return DispatchUnitStateEnum.GO_LOAD_QUEUE_POINT;
                }
                if (vehicleTask.getHelper().getDispatchStateManager().isDispatchLoadState()) {
                    return DispatchUnitStateEnum.GO_UNLOAD_QUEUE_POINT;
                }
            }
        }
        return null;
    }

    /**
     * 根据当前任务判断起始任务,true为可执行，false则清除当前任务的保存
     */
    private boolean judgeCurTask() {
        SemiStatic curArea = vehicleTask.getHelper().getCurArea();
        //特殊区域判断
        boolean noLoadState = vehicleTask.getHelper().getDispatchStateManager().isDispatchNoLoadState();//空载
        if (noLoadState && null != curArea && AreaTypeEnum.LOAD_AREA.equals(curArea.getAreaType())) {//在装载区内
            Unit unit = getUnit();
            if (null != unit) {//是调度单元的区域内
                if (null != unit.getLoadArea() && unit.getLoadArea().getLoadAreaId().equals(curArea.getId())) {
                    this.curState = DispatchUnitStateEnum.LOAD_WAIT_INTO_COMMAND;//等待进车信号
                    return true;
                }
            }
        }
        boolean loadState = vehicleTask.getHelper().getDispatchStateManager().isDispatchLoadState();//重载
        if (loadState && null != curArea && (AreaTypeEnum.UNLOAD_WASTE_AREA.equals(curArea.getAreaType()) ||
                AreaTypeEnum.UNLOAD_MINERAL_AREA.equals(curArea.getAreaType()))) {
            Unit unit = getUnit();
            if (null != unit) {//是调度单元的区域内
                if (null != unit.getUnloadArea() && unit.getUnloadArea().getUnloadAreaId().equals(curArea.getId())) {
                    this.curState = DispatchUnitStateEnum.UNLOAD_WAIT_INTO_COMMAND;//等待进车信号
                    return true;
                }
            }
        }
        if (null != curState) {
            switch (curState) {
                case GO_LOAD_TASK_POINT:
                case PREPARE_LOAD:
                case EXEC_LOAD://需要判断在装载区内
                    if (!curState.equals(DispatchUnitStateEnum.GO_LOAD_TASK_POINT)) {
                        curState = DispatchUnitStateEnum.GO_LOAD_TASK_POINT;
                    }
                    if (AreaTypeEnum.LOAD_AREA.equals(curArea.getAreaType())) {
                        return true;
                    }
                    break;
                case GO_UNLOAD_TASK_POINT:
                case PREPARE_UNLOAD:
                case EXEC_UNLOAD://需要判断在卸载区内
                    if (!curState.equals(DispatchUnitStateEnum.GO_UNLOAD_TASK_POINT)) {
                        curState = DispatchUnitStateEnum.GO_UNLOAD_TASK_POINT;
                    }
                    if (AreaTypeEnum.UNLOAD_WASTE_AREA.equals(curArea.getAreaType()) ||
                            AreaTypeEnum.UNLOAD_MINERAL_AREA.equals(curArea.getAreaType())) {
                        return true;
                    }
                    break;
                case LOAD_WAIT_INTO_COMMAND:
                case UNLOAD_WAIT_INTO_COMMAND:
                    return true;
            }
        }
        return false;
    }


    /**
     * 获取车辆当前任务状态
     */
    @Override
    public DispatchUnitStateEnum getTaskState() {
        return curState;
    }

    @Override
    public boolean changeTaskState(IEnum<String> state, IEnum<String>... expects) {
        DispatchUnitStateEnum unitStateEnum = (DispatchUnitStateEnum) state;
        if (!DispatchUnitStateEnum.FREE.equals(unitStateEnum)) {
            curState = unitStateEnum;
            setCurTask(curState);
        }
        return super.changeTaskState(state, expects);
    }

    @Override
    public DispatchUnitStateEnum getTaskCodeEnum(String value) {
        return DispatchUnitStateEnum.getEnum(value);
    }

    @Override
    public String getTaskCode(DispatchUnitStateEnum codeEnum) {
        return codeEnum.getValue();
    }

    @Override
    public String toString() {
        return BaseUtil.format("调度单元[{}],车辆[{}]", unit, getVehicleId());
    }

    public int hashCode() {
        return super.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof DispatchUnitInput) {
            DispatchUnitInput unitInput = (DispatchUnitInput) obj;
            return this.getVehicleId().equals(unitInput.getVehicleId());
        }
        return false;
    }

    @Override
    public String getDesc() {
        return "调度单元输入源";
    }
}
