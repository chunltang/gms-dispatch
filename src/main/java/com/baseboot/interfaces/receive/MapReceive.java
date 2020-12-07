package com.baseboot.interfaces.receive;

import com.baseboot.common.config.QueueConfig;
import com.baseboot.common.service.MqService;
import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.ByteUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.CalculatedValue;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.area.LoadArea;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.dispatch.path.GlobalPath;
import com.baseboot.entry.dispatch.path.VehicleTrail;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.entry.global.*;
import com.baseboot.entry.map.Point;
import com.baseboot.enums.LoadAreaStateEnum;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.interfaces.send.MapSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.dispatch.input.InputCache;
import com.baseboot.service.dispatch.manager.PathErrorEnum;
import com.baseboot.service.dispatch.manager.PathManager;
import com.baseboot.service.dispatch.manager.PathStateEnum;
import com.baseboot.service.init.InitMethod;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

@Slf4j
public class MapReceive {

    private final static MapReceive instance = new MapReceive();

    public static void dispense(byte[] message, String routeKey, String messageId) {
        log.debug("收到地图层消息,routeKey={}", routeKey);
        try {
            Method method = instance.getClass().getDeclaredMethod(routeKey, byte[].class, String.class);
            method.setAccessible(true);
            method.invoke(instance, message, messageId);
        } catch (NoSuchMethodException e) {
            log.error("MapReceive has not [{}] method", routeKey, e);
        } catch (IllegalAccessException e) {
            log.error("MapReceive access error [{}] method", routeKey, e);
        } catch (InvocationTargetException e) {
            log.error("MapReceive call error [{}] method", routeKey, e);
        }
    }

    /**
     * 接收地图更新通知
     */
    public void notifyGetMap(byte[] message, String messageId) {
        log.debug("接收地图更新通知");
        BaseUtil.cancelDelayTask(TimerCommand.MAP_LOAD_COMMAND);
        String msg = new String(message);
        HashMap params = BaseUtil.toObj(msg, HashMap.class);
        if (BaseUtil.mapNotNull(params) && params.containsKey("mapId")) {
            Object mapId = params.get("mapId");
            log.debug("加载活动地图,mapId=【{}】", mapId);
            InitMethod.mapInit(BaseUtil.typeTransform(mapId, Integer.class), true);
            Response response = new Response().withSucMessage("收到地图通知").withMessageId(messageId).withRouteKey("notifyGetMap").withToWho(QueueConfig.RESPONSE_MAP);
            MqService.response(response);
        } else {
            log.error("参数异常!");
        }
    }

    /**
     * 接收半静态层数据
     * {@link InitMethod#mapInit}
     */
    public void getSemiStaticLayerInfo(byte[] message, String messageId) {
        log.debug("接收半静态层数据");
        BaseUtil.cancelDelayTask(TimerCommand.MAP_LOAD_COMMAND);
        BaseCacheUtil.initSemiStatic(new String(message));
    }

    /**
     * 接收地图全局路径
     * {@link MapSend#getGlobalPath}
     */
    public void getGlobalPath(byte[] message, String messageId) {
        if (!BaseUtil.isExistLikeTask(messageId)) {
            log.error("没有查询到请求消息定时器,messageId={}", messageId);
            return;
        }
        log.debug("收到全局路径，messageId={}", messageId);
        BaseUtil.cancelDelayLikeTask(messageId);//取消定时器
        byte[] bytes = new byte[4];
        System.arraycopy(message, 0, bytes, 0, 4);
        int firstIntEndian = ByteUtil.bytes2IntBigEndian(bytes);
        if (firstIntEndian == 1 || Integer.reverseBytes(firstIntEndian) == 1) {
            GlobalPath globalPath = GlobalPath.createGlobalPath(message);
            boolean path = BaseCacheUtil.addGlobalPath(globalPath);
            int vehicleId = globalPath.getVehicleId();
            String key = TimerCommand.getTemporaryKey(vehicleId, TimerCommand.PATH_REQUEST_COMMAND);
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            PathManager pathManager = vehicleTask.getHelper().getPathManager();
            if (!path || !BaseUtil.CollectionNotNull(globalPath.getVertexs())) {//生成失败
                log.debug("【全局路径消息】,路径点数为0，状态：{}", globalPath.getStatus());
                boolean result = checkCurPoint(vehicleId);
                if (!result) {
                    log.error("全局路径生成失败，路径点数为空，未达到终点!");
                    pathManager.pathCreateError(PathErrorEnum.getPathErrorCode(String.valueOf(globalPath.getStatus())));
                    String format = BaseUtil.format("【{}】接收地图全局路径,异常状态码:{}", vehicleId, globalPath.getStatus());
                    LogUtil.addLogToRedis(LogType.ERROR, "map-" + vehicleId, format);
                } else {//直接修改为路径完成状态
                    if (pathManager.isValState(PathStateEnum.PATH_CREATING)) {
                        pathManager.pathClear();
                    }
                }
                Response msg = BaseCacheUtil.getResponseMessage(key);
                if (null != msg) {//交互式反馈结果
                    BaseCacheUtil.removeResponseMessage(key);
                    msg.withCode(String.valueOf(globalPath.getStatus()));
                    MqService.response(msg);
                }
            } else {//生成成功
                if (pathManager.isValState(PathStateEnum.PATH_CREATING)) {
                    boolean result = pathManager.pathCreated();
                    if (result) {
                        //插入路径
                        RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.VAP_PATH_PREFIX + globalPath.getVehicleId(), globalPath.toDataString());
                        String format = BaseUtil.format("【{}】接收地图全局路径,路径点集个数:{}", vehicleId, globalPath.getVertexNum());
                        LogUtil.addLogToRedis(LogType.INTERFACE_RESPONSE, "map-" + vehicleId, format);
                        Response msg = BaseCacheUtil.getResponseMessage(key);
                        if (null != msg) {//交互式反馈结果
                            BaseCacheUtil.removeResponseMessage(key);
                            msg.withSucMessage("路径生成成功");
                            MqService.response(msg);
                        }
                    } else {
                        BaseCacheUtil.removeGlobalPath(globalPath.getVehicleId());
                    }
                }
            }
        } else {
            log.error("【全局路径生成失败】,messageId={},message={}", messageId, new String(message));
        }
    }

    public static void fittingGlobalPath(byte[] message, String messageId) {
        log.debug("收到拟合路径，messageId={}", messageId);
        byte[] bytes = new byte[4];
        System.arraycopy(message, 0, bytes, 0, 4);
        int firstIntEndian = ByteUtil.bytes2IntBigEndian(bytes);
        if (firstIntEndian == 1 || Integer.reverseBytes(firstIntEndian) == 1) {
            GlobalPath.createFittingGlobalPath(message);
        }
    }


    /**
     * 判断车辆当前位置和目标位置,true为到达目标点
     */
    private boolean checkCurPoint(Integer vehicleId) {
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            Point curLocation = vehicleTask.getCurLocation();
            Point endPoint = InputCache.getEndPoint(vehicleId);
            double distance = DispatchUtil.twoPointDistance(curLocation, endPoint);
            return distance < CalculatedValue.END_DISTANCE_THRESHOLD;
        }
        return false;
    }


    /**
     * 接收轨迹
     * {@link VehicleTask#getTrajectoryByIdx}
     */
    public void getTrajectoryByIdx(byte[] message, String messageId) {
        Request request = BaseCacheUtil.getRequestMessage(messageId);
        if (null == request) {
            log.error("非法的轨迹数据!");
            return;
        }

        VehicleTrail newTrail = VehicleTrail.createVehicleTrail(message);
        if (null != newTrail) {
            int vehicleId = newTrail.getVehicleId();
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            if (null == vehicleTask) {
                log.error("地图轨迹数据异常，车辆编号[{}]不存在!,", vehicleId);
                return;
            }
            if (newTrail.getStatus() != 0) {
                String format = BaseUtil.format("【{}】轨迹生成异常，状态码:[{}]", newTrail.getVehicleId(), newTrail.getStatus());
                log.error(format);
                LogUtil.addLogToRedis(LogType.ERROR, "map", format);
            }
            WorkPathInfo workPathInfo = BaseCacheUtil.getWorkPathInfo(vehicleId);
            if (null == workPathInfo || !workPathInfo.permitIsSendTrail()) {//不发送轨迹
                log.error("全局路径信息不存在，不允许下发轨迹!");
                vehicleTask.getHelper().getVehicleStateManager().stopTask();
                return;
            }
            //缓存
            BaseCacheUtil.addVehicleTrail(newTrail);
            //插入轨迹
            RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.VAP_TRAIL_PREFIX + vehicleId, newTrail.toDataString(), BaseConstant.REDIS_EXPIRATION_TIME);
            sendTrail(newTrail);
        } else {
            log.error("轨迹数据异常，生成轨迹对象失败!");
        }
    }


    public static void sendTrail(VehicleTrail newTrail) {
        int num = newTrail.getVertexNum();
        if (num > 0 && newTrail.getVertexs().size() > 0) {
            CommSend.vehAutoTrailFollowing(newTrail.getVehicleId(), newTrail.toByteArray());
            WorkPathInfo info = BaseCacheUtil.getWorkPathInfo(newTrail.getVehicleId());
            if (null == info) {
                log.error("【{}】发送轨迹异常", newTrail.getVehicleId());
                return;
            }
            log.debug("【{}】获取轨迹:总点数{},轨迹编号:{}，轨迹起点x:{},y:{}，轨迹终点x:{},y:{},轨迹长度s:{}", newTrail.getVehicleId(), num, info.getNearestId(), newTrail.getVertexs().get(0).getX(), newTrail.getVertexs().get(0).getY(), newTrail.getVertexs().get(num - 1).getX(), newTrail.getVertexs().get(num - 1).getY(), newTrail.getVertexs().get(num - 1).getS());
        }
    }

    /**
     * 获取装载角度
     */
    public static void getDipTaskAngle(byte[] message, String messageId) {
        String msg = new String(message);
        log.debug("获取装载角度，消息:{}", msg);
        HashMap params = BaseUtil.toObj(msg, HashMap.class);
        Integer status = BaseUtil.mapGet(params, "status", Integer.class);
        Integer loadId = BaseUtil.mapGet(params, "loadId", Integer.class);
        String id = TimerCommand.VEHICLE_TEMPORARY_TASK_PREFIX + "getDipTaskAngle_" + loadId;
        LoadArea loadArea = BaseCacheUtil.getTaskArea(loadId);
        if (null == loadArea) {
            log.error("【{}】装载区不存在!", loadId);
            return;
        }

        if (null != status && 0 != status) {
            log.error("获取装载角度失败");
            BaseUtil.cancelDelayTask(id);
            String format = BaseUtil.format("【{}】装载区获取装载角度,结果状态:{}", loadId, status);
            LogUtil.addLogToRedis(LogType.ERROR, "map-" + loadId, format);
            loadArea.setAreaState(LoadAreaStateEnum.DELAY);
            return;
        }


        if (!BaseUtil.isExistLikeTask(id)) {
            log.error("获取装载角度任务过期!");
            return;
        }
        String taskPointJson = BaseUtil.toJson(params.get("taskPoint"));
        Point point = BaseUtil.toObj(taskPointJson, Point.class);
        String format = BaseUtil.format("【{}】装载区获取装载角度,结果:{}", loadId, taskPointJson);
        LogUtil.addLogToRedis(LogType.INFO, "map-" + loadId, format);
        log.debug("【{}】装载区动态设置装载点:{}", loadId, point);
        loadArea.dynamicLocation(point);
        BaseUtil.cancelDelayTask(id);
    }
}
