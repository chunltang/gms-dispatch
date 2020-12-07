package com.baseboot.service.dispatch.input;

import com.baseboot.common.service.MqService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.Response;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.manager.PathManager;
import lombok.extern.slf4j.Slf4j;

/**
 * 交互式输入
 */
@Slf4j
public class InteractiveInput extends DispatchInputAdapter<InteractiveStateEnum> {

    private Response response;

    /**
     * 交互式创建路径,入口
     */
    public boolean startTask(Integer vehicleId, Response response) {
        if (!BaseUtil.allObjNotNull(response, vehicleId)) {
            response.withFailMessage("参数异常");
            return false;
        }
        this.response = response;
        log.debug("车辆[{}]交互式路径请求...", vehicleId);
        PathManager pathManager = InputCache.getPathManager(vehicleId);
        if (null != pathManager) {
            if (pathManager.clearInterrupt()) {
                return createPath(0);
            }
        }
        response.withFailMessage("绕障路径生成失败");
        return false;
    }

    @Override
    public boolean createPath(int planType) {
        return super.createPath(planType);
    }

    @Override
    public void startRun() {
        super.startRun();
    }

    /**
     * 交互式停止
     */
    @Override
    public void stopRun() {
        super.stopRun();
    }


    /**
     * 交互式开始创建路径通知
     */
    @Override
    public void startCreatePathNotify() {
        super.startCreatePathNotify();
        changeTaskState(InteractiveStateEnum.PATH_CREATING, InteractiveStateEnum.FREE);
    }

    /**
     * 交互式路径创建成功通知
     */
    @Override
    public void createPathSuccessNotify() {
        super.createPathSuccessNotify();
        changeTaskState(InteractiveStateEnum.PATH_CREATED_SUCESS, InteractiveStateEnum.PATH_CREATING);
        if (null != response) {
            response.withSucMessage("全局路径生成成功");
            MqService.response(response);
        }
    }

    /**
     * 交互式路径创建异常通知，转为空闲
     */
    @Override
    public void createPathErrorNotify() {
        super.createPathErrorNotify();
        VehicleTask task = BaseCacheUtil.getVehicleTask(getVehicleId());
        if (null != task) {
            task.getHelper().getVehicleStateManager().stopTask();
        }
    }

    @Override
    public void runErrorNotify() {
        super.runErrorNotify();
    }

    /**
     * 交互式开始通知
     */
    @Override
    public void startRunNotify() {
        super.startRunNotify();
        this.response = null;
        changeTaskState(InteractiveStateEnum.PATH_RUNING, InteractiveStateEnum.PATH_CREATED_SUCESS);
    }

    /**
     * 交互式停止通知
     */
    @Override
    public void stopRunNotify() {
        super.stopRunNotify();
        changeTaskState(InteractiveStateEnum.PATH_INTERRUPT);
    }

    /**
     * 交互式到达通知，改为空闲
     */
    @Override
    public void arriveNotify() {
        super.arriveNotify();
        changeTaskState(InteractiveStateEnum.FREE);
    }

    /**
     * 获取车辆当前任务状态
     */
    @Override
    public InteractiveStateEnum getTaskState() {
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(getVehicleId());
        if (null != vehicleTask) {
            String taskState = vehicleTask.getHelper().getTaskStateManager().getTaskState();
            return getTaskCodeEnum(taskState);
        }
        log.error("获取当前车辆[{}]工作状态失败!", getVehicleId());
        return null;
    }

    @Override
    public InteractiveStateEnum getTaskCodeEnum(String value) {
        return InteractiveStateEnum.getEnum(value);
    }

    @Override
    public String getTaskCode(InteractiveStateEnum codeEnum) {
        return codeEnum.getValue();
    }

    @Override
    public String getDesc() {
        return "交互式输入源";
    }
}
