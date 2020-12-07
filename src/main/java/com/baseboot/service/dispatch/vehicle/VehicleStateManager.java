package com.baseboot.service.dispatch.vehicle;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.BasePool;
import com.baseboot.entry.global.Response;
import com.baseboot.enums.ConnectionStateEnum;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.input.DefaultInput;
import com.baseboot.service.dispatch.input.DispatchInput;
import com.baseboot.service.dispatch.input.InputCache;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 车辆状态管理
 */
@Data
@Slf4j
public class VehicleStateManager {

    private VehicleTaskHelper helper;

    private CommandStateManager commandStateManager;

    private DispatchStateManager dispatchStateManager;

    private InputStateManager inputStateManager;

    private RunStateManager runStateManager;

    private TaskStateManager taskStateManager;

    private Integer vehicleId;

    public VehicleStateManager(VehicleTaskHelper helper) {
        this.helper = helper;
        this.vehicleId = helper.getVehicleId();
        commandStateManager = new CommandStateManager(helper);
        dispatchStateManager = new DispatchStateManager(helper);
        inputStateManager = new InputStateManager(helper);
        runStateManager = new RunStateManager(helper);
        taskStateManager = new TaskStateManager(helper);
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            log.debug("*********************【{}】断开连接，重置运行状态*********************", helper.getVehicleId());
            helper.getHelpClazz().setVehicleState(ConnectionStateEnum.OFFLINE);
            helper.getRunStateManager().resetRunState();
            //取消工作线程
            BasePool.cancel(BaseConstant.MONITOR_VEHICLE_MESSAGE_PREFIX + vehicleId);
        }
    }


    /**
     * 是否允许开始任务,前端
     *
     * @param inputClass 任务类型
     */
    public boolean isStartTask(Integer vehicleId, Class<? extends DispatchInput> inputClass, Response response) {
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            try {
                DispatchInput input = switchTask(vehicleId, inputClass);
                if (null != input) {
                    boolean startTask = input.startTask(vehicleId, response);
                    if (!startTask) {
                        switchTask(vehicleId, DefaultInput.class);
                    }
                    return startTask;
                }
            } catch (Exception e) {
                log.error("【{}】切换任务失败", helper.getVehicleId(), e);
                return false;
            }
        }
        return false;
    }

    /**
     * 默认状态无条件切换
     */
    public DispatchInput switchTask(Integer vehicleId, Class<? extends DispatchInput> inputClass) {
        DispatchInput input = null;
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            try {
                if (helper.getInputStateManager().getCurInputType().getClass().equals(inputClass)) {
                    return helper.getInputStateManager().getCurInputType();
                }
                if (null != inputClass && (inputClass.equals(DefaultInput.class) || helper.getInputStateManager().isDefaultInput())) {
                    input = inputClass.newInstance();
                    log.debug("*********************【{}】切换任务:{}*********************", helper.getVehicleId(), input.getDesc());
                    helper.getPathManager().initInput(input);
                    InputCache.addDispatchInput(vehicleId, input);
                    return input;
                }
            } catch (Exception e) {
                log.error("【{}】切换任务失败", helper.getVehicleId());
            }
        }
        log.error("*********************【{}】切换任务失败!!!!!*********************", helper.getVehicleId());
        return input;
    }

    /**
     * 是否是空闲状态,并且不存在指令定时器，true
     */
    public boolean isFreeState() {
        return helper.getInputStateManager().isDefaultInput() &&
                !BaseUtil.isExistLikeTask(TimerCommand.VEHICLE_COMMAND_PREFIX);
    }

    /**
     * 根据输入判断是否可以下发任务
     */
    public boolean isCanRunState(Class<? extends DispatchInput> inputClass) {
        Class<? extends DispatchInput> aClass = helper.getInputStateManager().getCurInputType().getClass();
        return (aClass.equals(inputClass) || aClass.equals(DefaultInput.class)) &&
                !BaseUtil.isExistLikeTask(TimerCommand.VEHICLE_COMMAND_PREFIX);
    }


    /**
     * 停止任务，设置空闲状态
     */
    public void stopTask() {
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            if (!helper.getInputStateManager().isDefaultInput()) {
                log.debug("【{}】停止任务，设置空闲状态", vehicleId);
                BaseUtil.cancelDelayLikeTask(TimerCommand.VEHICLE_TEMPORARY_TASK_PREFIX + vehicleId);
                helper.getTaskStateManager().changeToFreeState();
                helper.getCommandStateManager().cancelPreCode();
            }
        }
    }

    /**
     * 开始生成路径提交判断,默认输入不能生成,true为能下发任务
     */
    public boolean startCreatePath() {
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            return !helper.getInputStateManager().isDefaultInput();
        }
    }

    /**
     * 生成路径没有响应,设置为空闲
     */
    public void createPathNoResponse() {
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            if (!helper.getInputStateManager().isDefaultInput()) {
                stopTask();
            }
        }
    }

    /**
     * 路径生成成功，默认模式不接收,true为接收,运行状态不接收
     */
    public boolean createSuccess() {
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            return !helper.getInputStateManager().isDefaultInput();
        }
    }

    /**
     * 路径生成异常，默认模式不处理,true接收
     */
    public boolean createPathError() {
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            return !helper.getInputStateManager().isDefaultInput();
        }
    }
}

