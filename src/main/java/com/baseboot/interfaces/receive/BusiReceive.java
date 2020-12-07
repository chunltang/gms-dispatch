package com.baseboot.interfaces.receive;

import com.baseboot.common.annotation.Parser;
import com.baseboot.common.config.QueueConfig;
import com.baseboot.common.service.DelayedService;
import com.baseboot.common.service.MqService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.DateUtil;
import com.baseboot.common.utils.RandomUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.area.LoadArea;
import com.baseboot.entry.dispatch.area.LoadPoint;
import com.baseboot.entry.dispatch.area.UnloadArea;
import com.baseboot.entry.dispatch.monitor.vehicle.Unit;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.Response;
import com.baseboot.entry.map.Point;
import com.baseboot.enums.AreaTypeEnum;
import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.input.*;
import com.baseboot.service.dispatch.vehicle.VehicleMonitorManager;
import com.baseboot.service.dispatch.vehicle.commandHandle.SystemModeCommand;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

@Slf4j
public class BusiReceive {

    private final static BusiReceive instance = new BusiReceive();

    public static void dispense(String message, String routeKey, String messageId) {
        log.debug("收到业务层消息,routeKey={}", routeKey);
        Response response = new Response().withRouteKey(routeKey).withMessageId(messageId).withToWho(QueueConfig.RESPONSE_BUSI);
        try {
            Method method = instance.getClass().getDeclaredMethod(BaseUtil.subIndexStr(routeKey, "."), String.class, Response.class);
            method.setAccessible(true);
            method.invoke(instance, message, response);
        } catch (NoSuchMethodException e) {
            log.error("BusiReceive has not [{}] method", routeKey, e);
        } catch (IllegalAccessException e) {
            log.error("BusiReceive access error [{}] method", routeKey, e);
        } catch (InvocationTargetException e) {
            log.error("BusiReceive call error [{}] method", routeKey, e);
        } finally {
            MqService.response(response);
        }
    }

    /**
     * 初始化车辆
     */
    @SuppressWarnings("unchecked")
    public void initVeh(String message, Response response) {
        BaseUtil.cancelDelayTask(TimerCommand.DISPATCH_INIT_COMMAND);
        HashMap<String, ArrayList> params = BaseUtil.toObj(message, HashMap.class);
        if (null != params && params.containsKey("vehicles")) {
            ArrayList<Integer> vehicleIds = BaseUtil.mapGet(params, "vehicles", ArrayList.class);
            BaseCacheUtil.initVehicles(vehicleIds);
            response.withSucMessage("初始化车辆成功");
        } else {
            response.withFailMessage("初始化车辆失败");
        }
    }


    /**
     * 创建调度单元
     */
    public void createLoaderAIUnit(String message, Response response) {
        log.debug("创建调度单元,{}", message);
        if (!BaseCacheUtil.isMapLoadFinish()) {
            response.withFailMessage("正在初始化区域信息...");
            return;
        }
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Unit unit = BaseUtil.toObj(message, Unit.class);
        if (null == unit) {
            response.withFailMessage("创建调度单元参数异常");
            return;
        }
        if (!BaseCacheUtil.checkMapData()) {
            response.withFailMessage("调度没有加载区域数据!");
            log.error("调度没有加载区域数据,不能创建调度单元!");
            return;
        }
        Integer loaderAreaId = BaseUtil.mapGet(params, "loaderAreaId", Integer.class);
        LoadArea loadArea = BaseCacheUtil.getTaskArea(loaderAreaId);
        if (null == loadArea) {
            response.withFailMessage("装载区不存在!");
            return;
        }
        unit.setLoadArea(loadArea);
        Integer unLoaderAreaId = BaseUtil.mapGet(params, "unLoaderAreaId", Integer.class);
        UnloadArea unLoadArea = BaseCacheUtil.getTaskArea(unLoaderAreaId);
        if (null == unLoadArea) {
            response.withFailMessage("卸载区不存在!");
            return;
        }
        unit.setUnloadArea(unLoadArea);

        if (!Parser.notNull(unit)) {
            log.error("创建调度单元参数异常!");
            response.withFailMessage("创建调度单元参数异常");
            return;
        }
        if (BaseCacheUtil.addUnit(unit)) {
            response.withSucMessage("创建调度单成功");
        } else {
            response.withFailMessage("该调度单元已存在");
        }
    }

    /**
     * 修改调度单元
     */
    public void modifyLoaderAIUnit(String message, Response response) {
        log.debug("修改调度单元,{}", message);
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer unitId = BaseUtil.mapGet(params, "unitId", Integer.class);
        Integer cycleTimes = BaseUtil.mapGet(params, "cycleTimes", Integer.class);
        String endTime = BaseUtil.mapGet(params, "endTime", String.class);
        Unit unit = BaseCacheUtil.getUnit(unitId);
        if (null == unit) {
            log.debug("调度单元不存在,{}", unitId);
            response.withFailMessage("调度单元不存在");
            return;
        }
        if (null != cycleTimes && cycleTimes >= 0) {
            unit.setCycleTimes(cycleTimes);
        }
        if (BaseUtil.StringNotNull(endTime)) {
            Date time = DateUtil.formatStringTime(endTime, DateUtil.FULL_TIME_SPLIT_PATTERN);
            if (null != time && time.getTime() > BaseUtil.getCurTime()) {
                unit.setEndTime(time);
            }
        }
        unit.updateCache();
        response.withSucMessage("修改调度单元成功");
    }

    /**
     * 删除调度单元
     */
    public void removeAIUnit(String message, Response response) {
        log.debug("删除调度单元,{}", message);
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer unitId = BaseUtil.mapGet(params, "unitId", Integer.class);
        Unit unit = BaseCacheUtil.getUnit(unitId);
        if (null == unit) {
            log.debug("调度单元不存在,{}", unitId);
            response.withFailMessage("调度单元不存在");
            return;
        }
        boolean notify = unit.delUnitNotify();
        if (notify) {
            response.withSucMessage("调度单元已删除");
        } else {
            response.withSucMessage("调度单元等待删除中");
        }
    }

    /**
     * 调度单元增加车辆
     */
    @SuppressWarnings("unchecked")
    public void addLoadAIVeh(String message, Response response) {
        log.debug("调度单元添加车辆,{}", message);
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        ArrayList<Integer> vehicleIds = BaseUtil.mapGet(params, "vehicleIds", ArrayList.class);
        Integer unitId = BaseUtil.mapGet(params, "unitId", Integer.class);
        Unit unit = BaseCacheUtil.getUnit(unitId);
        if (null == unit) {
            log.debug("调度单元不存在,{}", unitId);
            response.withFailMessage("调度单元不存在");
            return;
        }
        for (Integer vehicleId : vehicleIds) {
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            if (null == vehicleTask) {
                log.debug("系统中不存在该车辆信息,{}", unitId);
                continue;
            }
            unit.addVehicleTask(vehicleTask);
        }
        response.withSucMessage("调度单元添加车辆成功");
    }

    /**
     * 调度单元移除车辆
     */
    @SuppressWarnings("unchecked")
    public void removeAIVeh(String message, Response response) {
        log.debug("调度单元移除车辆,{}", message);
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        ArrayList<Integer> vehicleIds = BaseUtil.mapGet(params, "vehicleIds", ArrayList.class);
        Integer unitId = BaseUtil.mapGet(params, "unitId", Integer.class);
        Unit unit = BaseCacheUtil.getUnit(unitId);
        if (null == unit) {
            log.debug("调度单元不存在,{}", unitId);
            response.withFailMessage("调度单元不存在");
            return;
        }
        if (BaseUtil.CollectionNotNull(vehicleIds)) {
            for (Integer vehicleId : vehicleIds) {
                unit.removeVehicleTask(vehicleId);
            }
        }
        response.withSucMessage("调度单元移除车辆成功");
    }


    /**
     * 启动车辆,调度任务
     */
    public void startVehTask(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            log.debug("启动车辆，{}", message);
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            if (null == vehicleTask) {
                response.withFailMessage("该车辆不存在");
                log.debug("该车辆[{}]不存在", vehicleId);
                return;
            }
            if (!vehicleTask.getHelper().getVehicleMonitorManager().isSelfMode()) {
                response.withFailMessage("该车辆不是自动模式!");
                return;
            }

            if (!vehicleTask.getHelper().getVehicleStateManager().isCanRunState(DispatchUnitInput.class)) {
                response.withFailMessage("当前有任务正在执行!");
                return;
            }

            boolean startTask = vehicleTask.getHelper().getVehicleStateManager().isStartTask(vehicleId, DispatchUnitInput.class, response);
            if (!startTask) {
                response.withFailMessage("当前有任务正在执行!");
                return;
            }
            if (vehicleTask.getHelper().getVehicleMonitorManager().isReceiveObstacle()) {
                log.debug("车【{}】绕障路径启动运行!", vehicleId);
                vehicleTask.getHelper().getVehicleMonitorManager().setObstacleFlag(false);//修改路径下发限制
            }
        }
    }

    /**
     * 停止车辆
     */
    public void stopVehTask(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            log.debug("停止车辆，{}", message);
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            vehicleTask.getHelper().getVehicleStateManager().stopTask();
            response.withSucMessage("停止车辆成功");
        }

    }

    /**
     * 交互式请求
     */
    @SuppressWarnings("unchecked")
    public void createPath(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            log.debug("交互式请求，{}", message);
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            if (null == vehicleTask) {
                response.withFailMessage("没有配置车辆信息!");
                return;
            }
            if (!vehicleTask.getHelper().getVehicleMonitorManager().isSelfMode()) {
                response.withFailMessage("该车辆不是自动模式!");
                return;
            }

            if (!vehicleTask.getHelper().getVehicleStateManager().isCanRunState(InteractiveInput.class)) {
                response.withFailMessage("当前有任务正在执行!");
                return;
            }

            Integer planType = BaseUtil.mapGet(params, "planType", Integer.class);
            List<Map> points = BaseUtil.mapGet(params, "points", ArrayList.class);
            InputCache.addEndPoint(vehicleId, BaseUtil.map2Bean(points.get(0), Point.class));
            boolean result = vehicleTask.getHelper().getVehicleStateManager().isStartTask(vehicleId, InteractiveInput.class, response);
            if (!result) {
                log.debug("车辆[{}]交互式请求失败!", vehicleId);
                response.withFailMessage("交互式路径请求失败!");
            } else {
                String key = TimerCommand.getTemporaryKey(vehicleId, TimerCommand.PATH_REQUEST_COMMAND);
                BaseCacheUtil.addResponse(key, response);
                vehicleTask.getHelper().setTaskSpot(0);
                response.withSucMessage("交互式路径生成中...");
            }
        }

    }


    /**
     * 交互式路径运行
     */
    public void runPath(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            log.debug("交互式路径运行,{}", message);
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            if (null == vehicleTask) {
                response.withFailMessage("系统中没有改车辆信息");
                return;
            }
            if (!vehicleTask.getHelper().getVehicleMonitorManager().isSelfMode()) {
                response.withFailMessage("该车辆不是自动模式!");
                return;
            }
            if (vehicleTask.getHelper().getVehicleMonitorManager().isReceiveObstacle()) {
                log.debug("车【{}】绕障路径启动运行!", vehicleId);
                vehicleTask.getHelper().getVehicleMonitorManager().setObstacleFlag(false);//修改路径下发限制
            }
            boolean running = InputCache.getPathManager(vehicleId).pathRunning();
            if (running) {
                response.withSucMessage("交互式路径启动成功");
            } else {
                response.withFailMessage("交互式路径启动失败");
            }
            vehicleTask.startVehicle();
        }
    }

    /**
     * 取消交互式路径
     */
    public void stopVeh(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))) {
            log.debug("取消交互式路径,{}", message);
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            if (null == vehicleTask) {
                response.withFailMessage("系统中没有改车辆信息");
                return;
            }
            DispatchInput input = InputCache.getDispatchInput(vehicleId);
            if (input instanceof InteractiveInput) {
                String messageId = TimerCommand.getTemporaryKey(vehicleId, TimerCommand.PATH_REQUEST_COMMAND);
                BaseUtil.cancelDelayLikeTask(messageId);
                Response msg = BaseCacheUtil.getResponseMessage(messageId);
                if (null != msg) {
                    msg.withFailMessage("已取消交互式任务");
                    MqService.response(msg);
                }
                vehicleTask.getHelper().getVehicleStateManager().stopTask();
                response.withSucMessage("取消交互式任务成功");
                return;
            }
            response.withSucMessage("未执行交互式任务");
        }

    }

    /**
     * 进车信号
     */
    public void loadAreaEntry(String message, Response response) {
        log.debug("进车信号,{}", message);
        LoadArea loadArea = getLoadArea(message);
        if (null == loadArea) {
            response.withFailMessage("装载区不存在该任务点!");
            return;
        }
        boolean result = loadArea.loadAreaEntry();
        if (result) {
            response.withSucMessage("进车信号接收成功");
        } else {
            response.withFailMessage("进车信号发送失败");
        }
    }

    /**
     * 出车信号
     */
    public void loadAreaWorkDone(String message, Response response) {
        log.debug("出车信号,{}", message);
        LoadArea loadArea = getLoadArea(message);
        if (null == loadArea) {
            response.withFailMessage("装载区不存在该任务点!");
            return;
        }
        if(!loadArea.loadAreaWorkDone()){
            response.withFailMessage("发送出车信号失败!");
        }
        response.withSucMessage("出车信号接收成功");
    }

    /**
     * 任务点开装
     */
    public void loadAreaWorkBegin(String message, Response response) {
        log.debug("任务点开装,{}", message);
        LoadArea loadArea = getLoadArea(message);
        if (null == loadArea) {
            response.withFailMessage("装载区不存在该任务点!");
            return;
        }
        boolean areaWorkBegin = loadArea.loadAreaWorkBegin();
        if (areaWorkBegin) {
            response.withSucMessage("开始装载!");
        } else {
            response.withFailMessage("当前没有等待装载的车辆!");
        }
    }

    /**
     * 取消进车信号
     */
    public void loadAreaEntryCancel(String message, Response response) {
        log.debug("取消进车信号,{}", message);
        LoadArea loadArea = getLoadArea(message);
        if (null == loadArea) {
            response.withFailMessage("装载区不存在该任务点!");
            return;
        }
        boolean result = loadArea.loadAreaEntryCancel();
        if (result) {
            response.withSucMessage("取消进车信号接收成功");
        } else {
            response.withFailMessage("取消进车信号接收失败");
        }
    }


    /**
     * 任务点开工
     */
    public void taskSpotStart(String message, Response response) {
        log.debug("任务点开工,{}", message);
        LoadArea loadArea = getLoadArea(message);
        if (null == loadArea) {
            response.withFailMessage("装载区不存在该任务点!");
            return;
        }
        loadArea.taskSpotStart();
        response.withSucMessage("任务点开工接收成功");
    }

    /**
     * 任务点停工
     */
    public void taskSpotStop(String message, Response response) {
        log.debug("任务点停工,{}", message);
        LoadArea loadArea = getLoadArea(message);
        if (null == loadArea) {
            response.withFailMessage("装载区不存在该任务点!");
            return;
        }
        loadArea.taskSpotStop();
        response.withSucMessage("任务点停工接收成功");
    }

    /**
     * 根据任务点获取装载区
     */
    private LoadArea getLoadArea(String message) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer taskSpotId = BaseUtil.mapGet(params, "taskSpotId", Integer.class);
        if (null == taskSpotId) {
            return null;
        }
        List<LoadArea> loadAreas = BaseCacheUtil.getTaskArea(AreaTypeEnum.LOAD_AREA);
        for (LoadArea loadArea : loadAreas) {
            LoadPoint loadPoint = loadArea.getLoadPoint();
            if (null != loadPoint && taskSpotId.longValue() == loadPoint.getLoadId()) {
                return loadArea;
            }
        }
        return null;
    }

    /**
     * 直线紧急停车
     */
    public void emergencyStop(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            if (!vehicleTask.getHelper().getVehicleMonitorManager().isSelfMode()) {
                response.withFailMessage("车辆不是自动模式");
                return;
            } else if (!vehicleTask.getHelper().getRunStateManager().isStart() || !vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKDRIVING)) {
                response.withFailMessage("车辆是停止状态");
                return;
            }
            CommSend.vehRemoteEmergencyParking(vehicleId);
            response.withSucMessage("车辆紧急停车发送成功");
        } else {
            response.withFailMessage("车辆没有录入系统");
        }
    }

    /**
     * 缓解紧急停车
     */
    public void remissionEmergencyStop(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            if (!vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKNORMALPARKBYTRAJECTORY, TaskCodeEnum.TASKEMERGENCYPARKBYLINE, TaskCodeEnum.TASKEMERGENCYPARKBYTRAJECTORY)) {
                response.withFailMessage("车辆不是紧急停车状态");
                return;
            }
            CommSend.vehAutoClearEmergencyPark(vehicleId);
            response.withSucMessage("缓解车辆紧急停车发送成功");
        }
    }


    /**
     * 进入自动模式
     */
    public void automaticMode(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (vehicleTask.getHelper().getRunStateManager().isRunning()) {
            response.withFailMessage("车辆正在运行，不能进入自动模式!");
            return;
        }
        VehicleMonitorManager monitorManager = vehicleTask.getHelper().getVehicleMonitorManager();

        if (!monitorManager.isReceiveApplyFor()) {
            response.withFailMessage("没有收到自动驾驶模式申请!");
            return;
        }

        /*if (monitorManager.isAutoDriveMode()) {
            response.withSucMessage("进入自动模式成功");
            return;
        }*/
        monitorManager.getAttrCommand(SystemModeCommand.class).autoModeImPower();
        response.withSucMessage("自动模式发送成功");
    }

    /**
     * 进入人工模式
     */
    public void manualMode(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (vehicleTask.getHelper().getRunStateManager().isRunning()) {
            response.withFailMessage("车辆正在运行，不能进入人工模式!");
            return;
        }
        VehicleMonitorManager monitorManager = vehicleTask.getHelper().getVehicleMonitorManager();
        if (!monitorManager.isAutoDriveMode()) {
            response.withSucMessage("人工模式发送成功");
            return;
        }
        monitorManager.getAttrCommand(SystemModeCommand.class).manualModeImPower();
        response.withSucMessage("人工模式发送成功");
    }

    /**
     * 开启发动机
     */
    public void engineOn(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            String key = TimerCommand.VEHICLE_AUTO_ENGINE_ON_COMMAND + vehicleId;
            if (BaseUtil.isExistLikeTask(key)) {
                response.withSucMessage("正在开启发动机");
                return;
            }
            DelayedService.Task task = DelayedService.buildTask();
            task.withTask(() -> {
                //持续发送，直到静态测试或待机
                if (!vehicleTask.getHelper().getTaskCodeCommand().isContains(5, TaskCodeEnum.TASKSELFCHECK) ||
                        !vehicleTask.getHelper().getTaskCodeCommand().isContains(5, TaskCodeEnum.TASKSTANDBY)||
                        !vehicleTask.getHelper().getTaskCodeCommand().isContains(5, TaskCodeEnum.TASKSTATICTEST)) {
                    CommSend.vehAutoStart(vehicleId);
                } else {
                    task.withExec(false);
                }
            })
                    .withTaskId(key)
                    .withDelay(1000)
                    .withAtOnce(true)
                    .withNum(30)
                    .withDesc("发送开启发动机指令")
                    .withAfterTask(() -> {
                        if (task.getNum() == 0) {
                            log.error("【{}】开启发动机失败", vehicleId);
                        }
                    });
            DelayedService.addTask(task);
        }
        response.withSucMessage("开启发动机指令发送成功");
    }

    /**
     * 关闭发动机
     */
    public void engineOff(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            String key = TimerCommand.VEHICLE_AUTO_ENGINE_OFF_COMMAND + vehicleId;
            if (BaseUtil.isExistLikeTask(key)) {
                response.withSucMessage("正在关闭发动机");
                return;
            }
            DelayedService.Task task = DelayedService.buildTask();
            task.withTask(() -> {
                //关闭发动机直到状态为静默状态
                if (!vehicleTask.getHelper().getTaskCodeCommand().isContains(5, TaskCodeEnum.TASKSILENCE)) {
                    CommSend.vehAutoStop(vehicleId);
                } else {
                    task.withExec(false);
                }
            })
                    .withTaskId(key)
                    .withDelay(1000)
                    .withAtOnce(true)
                    .withNum(30)
                    .withDesc("发送关闭发动机指令")
                    .withAfterTask(() -> {
                        if (task.getNum() == 0) {
                            log.error("【{}】关闭发动机失败", vehicleId);
                        }
                    });
            DelayedService.addTask(task);
        }
        response.withSucMessage("关闭发动机指令发送成功");
    }

    /**
     * 设置为采集车
     */
    public void setCollectionVehicle(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer vehicleId = BaseUtil.mapGet(params, "vehicleId", Integer.class);
        Integer isCollectionVehicle = BaseUtil.mapGet(params, "isCollectionVehicle", Integer.class);
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            vehicleTask.getHelper().setCollectionVehicle(null != isCollectionVehicle && 1 == isCollectionVehicle);
        }
        log.debug("【{}】设置采集车状态成功,state=[{}]", vehicleId, isCollectionVehicle);
        response.withSucMessage(BaseUtil.format("[{}]设置采集车状态成功,state=[{}]", vehicleId, isCollectionVehicle));
    }

    /**
     * tcltd 申请更换矿种 未完成
     */
    public void changeLoadType(String message, Response response) {
        HashMap params = BaseUtil.toObj(message, HashMap.class);
        Integer unitId = BaseUtil.mapGet(params, "unitId", Integer.class);
        Integer unLoaderAreaId = BaseUtil.mapGet(params, "unLoaderAreaId", Integer.class);
        Integer loaderAreaId = BaseUtil.mapGet(params, "loaderAreaId", Integer.class);
    }
}
