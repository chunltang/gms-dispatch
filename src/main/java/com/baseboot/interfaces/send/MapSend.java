package com.baseboot.interfaces.send;

import com.baseboot.common.config.QueueConfig;
import com.baseboot.common.service.DelayedService;
import com.baseboot.common.service.MqService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.common.utils.RandomUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.LogType;
import com.baseboot.entry.global.Request;
import com.baseboot.entry.global.Response;
import com.baseboot.entry.map.Point;
import com.baseboot.interfaces.receive.MapReceive;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.dispatch.manager.PathErrorEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MapSend {

    /**
     * 初始地图区域信息
     */
    public static void initMapAreaInfo(int mapId) {
        Request request = new Request();
        Map<String, Object> params = new HashMap<>();
        params.put("mapId", mapId);
        request.withToWho(QueueConfig.REQUEST_MAP).withMessage(BaseUtil.toJson(params)).withRouteKey("getSemiStaticLayerInfo");
        MqService.request(request);
    }

    /**
     * 获取路径
     * {@link MapReceive#getGlobalPath}
     */
    public static void getGlobalPath(VehicleTask vehicleTask, Integer vehicleId, Point startPoint, Point endPoint, Integer planType) {
        String prefixKey = TimerCommand.getTemporaryKey(vehicleId, TimerCommand.PATH_REQUEST_COMMAND);
        String messageId = prefixKey + RandomUtil.randString(4);
        if (BaseUtil.isExistLikeTask(prefixKey)) {
            log.warn("【{}】路径定时存在，不请求路径", vehicleId);
            return;
        }
        Map<String, Object> params = new HashMap<>();
        Integer mapId = DispatchUtil.getActivateMapId();
        if (null == mapId) {
            log.error("活动地图id不存在");
            return;
        }
        params.put("mapId", mapId);
        params.put("vehicleId", vehicleId);
        params.put("planType", planType);
        params.put("begin", startPoint);
        params.put("end", endPoint);
        Request request = new Request()
                .withRouteKey("getGlobalPath")
                .withToWho(QueueConfig.REQUEST_MAP)
                .withMessage(BaseUtil.toJson(params))
                .withMessageId(messageId);

        DelayedService.Task task = DelayedService.buildTask();
        task.withTask(() -> {
            log.error("请求全局路径，远程服务未响应!");
            Response response = BaseCacheUtil.getResponseMessage(prefixKey);
            if (null != response) {
                BaseCacheUtil.removeResponseMessage(prefixKey);
                response.withFailMessage("远程服务未响应");
                MqService.response(response);
            }
            vehicleTask.getHelper().getPathManager().pathCreateError(PathErrorEnum.TIMEOUT);
        }).withNum(1).withDesc("请求全局路径").withTaskId(messageId);
        //添加定时器
        DelayedService.addTask(task, 30 * 1000);
        MqService.request(request);

        String format = BaseUtil.format("【{}】获取路径,参数:{}", vehicleId, BaseUtil.toJson(params));
        LogUtil.addLogToRedis(LogType.INTERFACE_REQUEST, "map-" + vehicleId, format);
    }

    /**
     * 获取轨迹
     */
    public static void getTrajectory(Integer activateMapId, Integer vehicleId, double curSpeed, int startId, int endId, Point nowPoint) {
        Map<String, Object> params = new HashMap<>();
        params.put("mapId", activateMapId);
        params.put("vehicleId", vehicleId);
        params.put("nowSpeed", curSpeed);
        params.put("beginIdx", startId);
        params.put("endIdx", endId);
        params.put("nowPoint", nowPoint);
        Request request = new Request();
        request.withToWho(QueueConfig.RESPONSE_MAP).withRouteKey("getTrajectoryByIdx").withMessage(BaseUtil.toJson(params));
        MqService.request(request);
    }

    /**
     * 获取装载角度
     */
    public static void getDipTaskAngle(Point dipPoint, Point taskPoint, Integer loadId) {
        if (!BaseUtil.allObjNotNull(dipPoint, taskPoint)) {
            log.error("参数异常");
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("dipPoint", dipPoint);
        params.put("taskPoint", taskPoint);
        params.put("loadId", loadId);
        Request request = new Request();
        request.withToWho(QueueConfig.RESPONSE_MAP).withRouteKey("getDipTaskAngle").withMessage(BaseUtil.toJson(params));
        MqService.request(request);
        String format = BaseUtil.format("【{}】装载区获取装载角度,参数:{}", loadId, BaseUtil.toJson(params));
        LogUtil.addLogToRedis(LogType.INTERFACE_REQUEST, "map-" + loadId, format);
    }

    /**
     * 全局路径拟合
     */
    public static void fittingGlobalPath(Integer mapId, Integer vehicleId, Point nowPoint, Integer beginIdx) {
        if (!BaseUtil.allObjNotNull(mapId, vehicleId, nowPoint, beginIdx)) {
            log.error("参数异常");
            return;
        }
        DelayedService.addTaskNoExist(() -> {
        }, 5000, TimerCommand.getTemporaryKey(vehicleId, TimerCommand.FITTING_GLOBAL_PATH_COMMAND), false).withNum(1);

        Map<String, Object> params = new HashMap<>();
        params.put("mapId", mapId);
        params.put("vehicleId", vehicleId);
        params.put("nowPoint", nowPoint);
        params.put("beginIdx", beginIdx);
        Request request = new Request();
        request.withToWho(QueueConfig.RESPONSE_MAP).withRouteKey("fittingGlobalPath").withMessage(BaseUtil.toJson(params));
        MqService.request(request);
        String format = BaseUtil.format("【{}】全局路径拟合,参数:{}", vehicleId, BaseUtil.toJson(params));
        LogUtil.addLogToRedis(LogType.INTERFACE_REQUEST, "map-" + vehicleId, format);
    }
}
