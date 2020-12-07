package com.baseboot.service.init;

import com.alibaba.fastjson.JSONObject;
import com.baseboot.common.service.DelayedService;
import com.baseboot.common.service.MongoService;
import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.DateUtil;
import com.baseboot.common.utils.IOUtil;
import com.baseboot.entry.dispatch.TimerCommand;
import com.baseboot.entry.dispatch.monitor.LiveInfo;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorGpsInfo;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorHmiInfo;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorTask;
import com.baseboot.entry.dispatch.monitor.vehicle.TroubleParse;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleLiveInfo;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.BaseCache;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.MongoKeyPool;
import com.baseboot.entry.global.RedisKeyPool;
import com.baseboot.enums.ConnectionStateEnum;
import com.baseboot.interfaces.receive.CommReceive;
import com.baseboot.interfaces.receive.MapReceive;
import com.baseboot.interfaces.send.MapSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.dispatch.task.MongoStore;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipOutputStream;

@Slf4j
public class InitMethod {

    /**
     * 启动完成，执行代码压缩
     */
    public static void codeCompress() {
        String system = System.getProperty("os.name");
        if (!system.startsWith("Win")) {
            return;
        }
        String property = System.getProperty("user.dir");
        File dir = new File(property);
        List<File> files = new ArrayList<>();
        IOUtil.listFiles(dir, files, "target", "log");
        try (FileOutputStream fos = new FileOutputStream(new File(dir.getParentFile().getPath() + "/gms-dispatch.zip"));
             ZipOutputStream zos = new ZipOutputStream(fos, Charset.forName("utf-8"))) {
            IOUtil.fileCompress(files, zos, property);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查是否有调度程序启动
     */
    public static void checkService() {
        String str = RedisService.get(BaseConstant.KEEP_DB, RedisKeyPool.DISPATCH_SERVER_HEARTBEAT);
        if (null != str) {
            log.error("已存在运行中的调度程序，当前进程不允许启动!");
            System.exit(0);
        }
    }

    /**
     * 清理之前缓存的路径、轨迹
     */
    public static void clearCache() {
        RedisService.delPattern(BaseConstant.MONITOR_DB, RedisKeyPool.VAP_PATH_PREFIX);
        RedisService.delPattern(BaseConstant.MONITOR_DB, RedisKeyPool.VAP_BASE_PREFIX);
        RedisService.delPattern(BaseConstant.MONITOR_DB, RedisKeyPool.VAP_TRAIL_PREFIX);
    }

    /**
     * 添加心跳
     */
    public static void heartbeat() {
        RedisService.asyncSet(BaseConstant.KEEP_DB, RedisKeyPool.DISPATCH_SERVER_HEARTBEAT, DateUtil.formatLongToString(BaseUtil.getCurTime()), 10000);
    }

    /**
     * 初始化车辆、调度单元
     */
    public static void dispatchInit() {
        DelayedService.Task task = DelayedService.buildTask(InitMethod::dispatchInitTimer)
                .withTaskId(TimerCommand.DISPATCH_INIT_COMMAND)
                .withDelay(5000)
                .withAtOnce(true)
                .withPrintLog(true)
                .withDesc("调度初始化")
                .withNum(-1);
        DelayedService.addTask(task);
    }

    private static void dispatchInitTimer() {
        String time = DateUtil.formatLongToString(System.currentTimeMillis());
        RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.DISPATCH_SERVER_INIT, time);
    }

    /**
     * 初始化地图任务区
     * {@link MapReceive#getSemiStaticLayerInfo}
     */
    private static void mapInitTimer(Integer mapId) {
        if (null == mapId) {
            mapId = DispatchUtil.getActivateMapId();
            if (null == mapId) {
                log.error("******************获取活动地图id失败,初始化任务区失败!******************");
                return;
            }
        }
        MapSend.initMapAreaInfo(mapId);
    }

    public static void mapInit(Integer mapId, boolean atOnce) {
        //初始化地图原点
        Runnable task = () -> InitMethod.mapInitTimer(mapId);
        DelayedService.addTaskNoExist(task, 10000, TimerCommand.MAP_LOAD_COMMAND, atOnce)
                .withNum(-1)
                .withDesc("地图任务区初始化")
                .withPrintLog(true);
    }

    /**
     * 实时信息检查是否离线
     */
    public static void linkCheck() {
        Map<Integer, LiveInfo> liveCache = BaseCache.DEVICE_LIVE_CACHE;
        liveCache.forEach((id, info) -> {
            if (info.isLinkFlag() && BaseUtil.getCurTime() - info.getReceiveTime() > BaseConstant.VEHICLE_EXPIRATION_TIME) {
                log.error("【{}】设备断开连接", id);
                info.setLinkFlag(false);
                if (info instanceof VehicleLiveInfo) {//如果是无人驾驶，重置运行状态
                    VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(id);
                    if (null != vehicleTask) {
                        vehicleTask.getHelper().getVehicleStateManager().disconnect();
                    }
                } else if (info instanceof ExcavatorHmiInfo || info instanceof ExcavatorGpsInfo) {
                    ExcavatorTask excavatorTask = BaseCacheUtil.getExcavator(id);
                    excavatorTask.changeMonitorState(ConnectionStateEnum.OFFLINE);
                }
            }
        });
    }

    /**
     * 缓存数据测试
     */
    public static void getMonitorCache() {
        CommReceive receive = new CommReceive();
        //receive.getMonitorCache();
    }

    /**
     * 初始化车载故障json文件
     */
    @SuppressWarnings("unchecked")
    public static void readVehTroubleFile() {
        String stream = IOUtil.inputStreamToString(InitMethod.class.getResourceAsStream("/json/veh-trouble.json"));
        if (BaseUtil.StringNotNull(stream)) {
            Map<String, Object> innerMap = JSONObject.parseObject(stream).getInnerMap();
            Map<String, Object> troubleMap = (Map) innerMap.get("all");
            Map<String, TroubleParse> troubleEntryMap = new HashMap<>();
            troubleMap.forEach((key, val) -> {
                TroubleParse entry = new TroubleParse();
                entry.setId(key);
                entry.setDesc(((Map) val).get("part").toString());
                Map<String, String> keyVal = entry.getKeyVal();
                Map<String, List> faultType = (Map) ((Map) val).get("fault_type");
                faultType.forEach((k, v) -> {
                    keyVal.put(k.toString(), v.get(0).toString());
                });
                troubleEntryMap.put(entry.getId(), entry);
            });
            BaseCacheUtil.addVehicleTrouble(troubleEntryMap);
            return;
        }
        log.error("初始化车载故障json文件异常，文件不存在");
    }

    /**
     * 限时索引
     */
    public static void mongoTableIndex() {
        Collection<VehicleTask> vehicleTasks = BaseCacheUtil.getVehicleTasks();
        for (VehicleTask task : vehicleTasks) {
            Integer vehicleId = task.getVehicleId();
            List<String> pathIndex = MongoService.listIndex(MongoKeyPool.VEHICLE_GLOBAL_PATH_PREFIX + vehicleId);
            List<String> monitorIndex = MongoService.listIndex(MongoKeyPool.VEHICLE_MONITOR_PREFIX + vehicleId);
            List<String> trailIndex = MongoService.listIndex(MongoKeyPool.VEHICLE_TRAIL_PREFIX + vehicleId);
            existOrSetExpireIndex(pathIndex, MongoKeyPool.VEHICLE_GLOBAL_PATH_PREFIX + vehicleId, "expireAt", 30 * 86400);
            existOrSetExpireIndex(monitorIndex, MongoKeyPool.VEHICLE_MONITOR_PREFIX + vehicleId, "expireAt", 30 * 86400);
            existOrSetExpireIndex(trailIndex, MongoKeyPool.VEHICLE_TRAIL_PREFIX + vehicleId, "expireAt", 3 * 86400);
        }
    }

    private static void existOrSetExpireIndex(List<String> index, String tableName, String indexName, long expireTime) {//expireTime时间为秒
        if ((index.isEmpty() || Arrays.toString(index.toArray(new String[]{})).contains(indexName)) && MongoService.collectionExists(tableName)) {
            MongoService.createExpireIndex(tableName, "expireAt", expireTime, TimeUnit.SECONDS);
        }
    }
}
