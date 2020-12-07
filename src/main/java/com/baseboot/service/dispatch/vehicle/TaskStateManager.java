package com.baseboot.service.dispatch.vehicle;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.Unit;
import com.baseboot.entry.global.LogType;
import com.baseboot.enums.DispatchStateEnum;
import com.baseboot.enums.TaskTypeEnum;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.input.DefaultInput;
import com.baseboot.service.dispatch.input.DispatchInput;
import com.baseboot.service.dispatch.manager.PathStateEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class TaskStateManager {

    private final Integer vehicleId;

    private VehicleTaskHelper helper;

    public final static String FREE_STATE = "6";//空闲状态

    public TaskStateManager(VehicleTaskHelper helper) {
        this.helper = helper;
        this.vehicleId = helper.getVehicleId();
    }

    /**
     * 修改工作状态
     */
    public boolean changeTaskStateEnum(String taskState, String desc, String... except) {
        boolean result = false;
        if (null != taskState) {
            if (helper.getLiveInfo().getTaskState().get().equals(taskState)) {
                return true;
            }
            if (BaseUtil.arrayNotNull(except)) {
                for (String str : except) {
                    boolean compareAndSet = helper.getLiveInfo().getTaskState().compareAndSet(str, taskState);
                    if (compareAndSet) {
                        result = true;
                        log.debug("修改工作状态:{}={} 【{}】", vehicleId, taskState, desc);
                        break;
                    }
                }
            } else {
                helper.getLiveInfo().getTaskState().set(taskState);
                log.debug("修改工作状态:{}={} 【{}】", vehicleId, taskState, desc);
                result = true;
            }
        }
        if (result && FREE_STATE.equals(getTaskState())) {
            setCurTaskType(TaskTypeEnum.NONE);
        }
        helper.clearWebParams();
        return result;
    }

    /**
     * 修改为空闲状态,所有状态全部重置为默认
     */
    public void changeToFreeState() {
        log.debug("车辆[{}]改为【空闲状态】", vehicleId);
        //修改车辆标志位
        helper.getHelpClazz().changeStartFlag(false);
        //发送安全停车
        String obsDesc = BaseUtil.format("【{}】车辆改为空闲状态，下发安全停车", vehicleId);
        LogUtil.addLogToRedis(LogType.WARN, "monitor-" + vehicleId, obsDesc);
        CommSend.vehAutoSafeParking(vehicleId);
        //工作状态改为空闲状态
        helper.getLiveInfo().getTaskState().set(FREE_STATE);
        //修改默认输入
        helper.getVehicleStateManager().switchTask(vehicleId, DefaultInput.class);
        helper.getPathManager().changePathState(PathStateEnum.FREE);
        //清理路径和轨迹
        BaseCacheUtil.removeGlobalPath(vehicleId);
        //修改监控数据
        helper.getVehicleMonitorManager().resetMonitorInfo();
        //修改调度状态
        boolean dispatchLoadState = helper.getDispatchStateManager().isDispatchLoadState();
        if (dispatchLoadState) { //重载
            helper.getDispatchStateManager().changeDispatchState(DispatchStateEnum.LOAD_FREE);
        } else {//空载
            helper.getDispatchStateManager().changeDispatchState(DispatchStateEnum.NOLOAD_FREE);
        }
        //重置调度单元关联车辆
        Unit unit = helper.getSelfUnit();
        if (null != unit && null != unit.getLoadArea()) {
            unit.getLoadArea().cancelRelevance(vehicleId);
        }
    }


    /**
     * 判断工作状态是否为给定中的一个
     */
    public boolean isInputTaskState(Class<? extends DispatchInput> inputClass, String... states) {
        return helper.getInputStateManager().isGetInputType(inputClass) && Arrays.asList(states).contains(getTaskState());
    }

    /**
     * 获取工作状态
     */
    public String getTaskState() {
        return helper.getLiveInfo().getTaskState().get();
    }


    /**
     * 设置当前任务状态
     */
    public void setCurTaskType(TaskTypeEnum taskType) {
        helper.getLiveInfo().setTaskType(taskType);
    }
}
