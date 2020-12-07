package com.baseboot.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baseboot.common.service.MongoService;
import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.Assert;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.area.*;
import com.baseboot.entry.dispatch.monitor.LiveInfo;
import com.baseboot.entry.dispatch.monitor.Location;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorTask;
import com.baseboot.entry.dispatch.monitor.vehicle.TroubleParse;
import com.baseboot.entry.dispatch.monitor.vehicle.Unit;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.dispatch.path.GlobalPath;
import com.baseboot.entry.dispatch.path.VehicleTrail;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.entry.global.*;
import com.baseboot.entry.map.IdPoint;
import com.baseboot.entry.map.OriginPoint;
import com.baseboot.entry.map.SemiStatic;
import com.baseboot.enums.AreaTypeEnum;
import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.dispatch.vehicle.commandHandle.TaskCodeCommand;
import com.baseboot.service.dispatch.task.TriggerTask;
import com.baseboot.service.dispatch.manager.PathManager;
import com.baseboot.service.dispatch.manager.PathStateEnum;
import com.baseboot.service.init.InitMethod;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class BaseCacheUtil {

    /*********************************初始化数据**************************************/

    /**
     * 初始化所有车辆信息
     */
    public static synchronized void initVehicles(List<Integer> vehicleIds) {
        log.debug("初始化所有车辆信息");
        Map<Integer, VehicleTask> vehicleTaskCache = BaseCache.VEHICLE_TASK_CACHE;
        for (Integer vehicleId : vehicleIds) {
            if (vehicleTaskCache.containsKey(vehicleId)) {
                log.debug("该车辆[{}]已初始化", vehicleId);
                continue;
            }
            VehicleTask vehicleTask = new VehicleTask(vehicleId);
            vehicleTask.initHelper();
            vehicleTaskCache.put(vehicleId, vehicleTask);
        }
    }

    /**
     * 初始化半静态层数据,新版
     */
    public static synchronized void initSemiStatic(String message) {
        log.debug("初始化半静态层数据");
        Map<Integer, SemiStatic> semiStaticCache = BaseCache.SEMI_STATIC_CACHE;
        JSONObject jsonObj = BaseUtil.getJsonObj(message, "data");
        if (null != jsonObj) {
            Integer mapId = jsonObj.getInteger("id");
            Integer id = BaseCacheUtil.getActiveMapId();
            if (mapId.equals(id)) {
                log.debug("地图【{}】已加载,不重复加载!", mapId);
                return;
            }
            if (!initNewMap()) {
                return;
            }
            BaseCacheUtil.setActiveMapId(mapId);
            JSONArray pointArr = jsonObj.getJSONArray("map_point_set");
            setOriginPoint(jsonObj.getDouble("origin_x"), jsonObj.getDouble("origin_y"), jsonObj.getDouble("origin_z"));
            Map<Long, IdPoint> totalPoints = new HashMap<>();
            for (int i = 0; i < pointArr.size(); i++) {
                JSONObject pointObj = pointArr.getJSONObject(i);
                IdPoint idPoint = pointObj.toJavaObject(IdPoint.class);
                totalPoints.put(idPoint.getId(), idPoint);
            }

            JSONArray arcArr = jsonObj.getJSONArray("map_arc_set");
            JSONArray areaArr = jsonObj.getJSONArray("map_area_set");
            if (null != areaArr) {
                for (Object area : areaArr) {
                    SemiStatic semiStatic = BaseUtil.toObjIEnum(area, SemiStatic.class);
                    if (null != semiStatic) {
                        List<IdPoint> areaBorder = getAreaBorder(totalPoints, arcArr, semiStatic.getArcs());
                        semiStatic.setBorder(areaBorder);
                        semiStaticCache.put(semiStatic.getId(), semiStatic);
                    }
                }
            }
        }

        if (checkMapData()) {
            initTaskAreas();
            log.debug("【{}】活动地图加载成功", BaseCacheUtil.getActiveMapId());
            InitMethod.dispatchInit();
        } else {
            log.error("地图数据没有装载区、卸载区，重新加载!");
            BaseCacheUtil.setActiveMapId(0);
            InitMethod.mapInit(null, false);
        }
    }

    /**
     * 地图数据检查,true为通过
     */
    public static boolean checkMapData() {
        Map<Integer, SemiStatic> semiStaticCache = BaseCache.SEMI_STATIC_CACHE;
        boolean hasLoadArea = false;
        boolean hasUnLoadArea = false;
        for (SemiStatic semiStatic : semiStaticCache.values()) {
            if (semiStatic.getAreaType().equals(AreaTypeEnum.LOAD_AREA)) {
                hasLoadArea = true;
            }

            if (semiStatic.getAreaType().equals(AreaTypeEnum.UNLOAD_MINERAL_AREA) ||
                    semiStatic.getAreaType().equals(AreaTypeEnum.UNLOAD_WASTE_AREA)) {
                hasUnLoadArea = true;
            }
        }
        return hasLoadArea && hasUnLoadArea;
    }

    //查找区域对应的点集
    private static List<IdPoint> getAreaBorder(Map<Long, IdPoint> totalPoints, JSONArray arcArr, SemiStatic.Arc[] arcs) {
        List<IdPoint> points = new ArrayList<>();
        for (SemiStatic.Arc arc : arcs) {
            int arcId = arc.getArcId();
            int direction = arc.getDirection();
            for (int i = 0; i < arcArr.size(); i++) {
                JSONObject arcObj = arcArr.getJSONObject(i);
                int id = arcObj.getInteger("id");
                if (arcId == id) {
                    JSONArray arcPoints = arcObj.getJSONArray("arc_point_list");
                    if (!arcPoints.isEmpty()) {
                        Integer[] array = arcPoints.toArray(new Integer[]{});
                        if (direction == 1) {
                            Collections.reverse(Arrays.asList(array));
                        }
                        for (Integer pId : array) {
                            points.add(totalPoints.get(pId.longValue()));
                        }
                    }
                }
            }
        }
        return points;
    }


    /**
     * 初始化新地图
     */
    private static boolean initNewMap() {
        log.debug("加载活动地图,清理静态层和调度单元缓存数据");
        if (BaseCache.UNIT_CACHE.isEmpty()) {//调度单元为空，表示没有任务执行
            return true;
        }
        Map<Integer, VehicleTask> vehicleTaskCache = BaseCache.VEHICLE_TASK_CACHE;
        //停止车辆所有调度任务
        for (VehicleTask task : vehicleTaskCache.values()) {
            //断开连接的车和不是自动模式的车直接删除
            if (!task.getHelper().checkLink() || !task.getHelper().getVehicleMonitorManager().isSelfMode()) {
                task.getHelper().mapLoadClear();
            } else if (task.getHelper().getVehicleMonitorManager().isSelfMode()) {
                String format = BaseUtil.format("【{}】重新加载地图，下发安全停车", task.getVehicleId());
                log.warn(format);
                LogUtil.addLogToRedis(LogType.WARN,"map",format);
                CommSend.vehAutoSafeParking(task.getVehicleId());//停止任务并重置调度输入源
                TriggerTask triggerTask = new TriggerTask("initNewMap_" + task.getVehicleId(), 5000, () -> {
                    if (task.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKSTANDBY) ||
                            task.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKNORMALPARKBYTRAJECTORY)) {
                        task.getHelper().mapLoadClear();
                        return true;
                    }
                    return false;
                });
                TaskCodeCommand.addTask(task.getVehicleId(), triggerTask);
            }
        }
        //清理所有调度单元
        BaseCache.UNIT_CACHE.clear();
        BaseCache.SEMI_STATIC_CACHE.clear();
        //unitCache.values().removeIf(next -> !next.hasUnitVehicles());
        BaseUtil.TSleep(2000);
        return BaseCache.UNIT_CACHE.isEmpty();
    }

    public static void initTaskAreas() {
        log.debug("初始化所有任务区信息");
        BaseCache.TASK_AREA_CACHE.clear();
        Map<Integer, SemiStatic> semiStaticCache = BaseCache.SEMI_STATIC_CACHE;
        Map<Integer, TaskArea> taskAreaCache = BaseCache.TASK_AREA_CACHE;
        for (SemiStatic semiStatic : semiStaticCache.values()) {
            Integer areaId = semiStatic.getId();
            IdPoint queuePoint = semiStatic.getQueuePoint();
            List<SemiStatic.TaskSpot> funcPoint = semiStatic.getFuncPoint();
            AreaTypeEnum areaType = semiStatic.getAreaType();
            switch (areaType) {
                case LOAD_AREA:
                    LoadArea loadArea = new LoadArea();
                    loadArea.setLoadAreaId(areaId);
                    if (null != queuePoint) {
                        loadArea.setQueuePoint(new QueuePoint(queuePoint.getId(), queuePoint.getX(), queuePoint.getY(), queuePoint.getZ(), queuePoint.getYawAngle()));
                    }
                    if (BaseUtil.CollectionNotNull(funcPoint)) {
                        IdPoint point = funcPoint.get(0).getPoint();
                        loadArea.setLoadPoint(new LoadPoint(point.getId(), point.getX(), point.getY(), point.getZ(), point.getYawAngle()));
                    }
                    taskAreaCache.put(areaId, loadArea);
                    break;
                case UNLOAD_WASTE_AREA:
                    UnLoadWasteArea unLoadWasteArea = new UnLoadWasteArea();
                    if (BaseUtil.CollectionNotNull(funcPoint)) {
                        UnloadPoint[] unloadPoints = new UnloadPoint[funcPoint.size()];
                        for (int i = 0; i < funcPoint.size(); i++) {
                            IdPoint point = funcPoint.get(i).getPoint();
                            unloadPoints[i] = new UnloadPoint(point.getId(), point.getX(), point.getY(), point.getZ(), point.getYawAngle());

                        }
                        unLoadWasteArea.setUnloadPoints(unloadPoints);
                    }
                    if (null != queuePoint) {
                        unLoadWasteArea.setQueuePoint(new QueuePoint(queuePoint.getId(), queuePoint.getX(), queuePoint.getY(), queuePoint.getZ(), queuePoint.getYawAngle()));
                    }
                    taskAreaCache.put(areaId, unLoadWasteArea);
                    break;
                case UNLOAD_MINERAL_AREA:
                    UnLoadMineralArea unLoadMineralArea = new UnLoadMineralArea();
                    unLoadMineralArea.setUnloadAreaId(areaId);
                    if (null != queuePoint) {
                        unLoadMineralArea.setQueuePoint(new QueuePoint(queuePoint.getId(), queuePoint.getX(), queuePoint.getY(), queuePoint.getZ(), queuePoint.getYawAngle()));
                    }
                    if (BaseUtil.CollectionNotNull(funcPoint)) {
                        UnloadPoint[] unloadPoints = new UnloadPoint[funcPoint.size()];
                        int index = 0;
                        for (SemiStatic.TaskSpot taskSpot : funcPoint) {
                            IdPoint point = taskSpot.getPoint();
                            unloadPoints[index] = new UnloadPoint(point.getId(), point.getX(), point.getY(), point.getZ(), point.getYawAngle());
                            index++;
                        }
                        unLoadMineralArea.setUnloadPoints(unloadPoints);
                    }
                    taskAreaCache.put(areaId, unLoadMineralArea);
                    break;
            }
        }
        taskAreaCache.forEach((key, val) -> {
            log.debug("初始化地图区域:{}", val.toString());
            val.updateCache();
        });
    }

    //Set<LoadArea> taskArea = getTaskArea(AreaTypeEnum.LOAD_AREA);
    @SuppressWarnings("unchecked")
    public static <T> List<T> getTaskArea(AreaTypeEnum areaType) {
        Map<Integer, TaskArea> taskAreaCache = BaseCache.TASK_AREA_CACHE;
        return (List<T>) taskAreaCache.values().stream().filter(ta -> {
            return areaType.equals(ta.getAreaType());
        }).map(TaskArea::getInstance).collect(Collectors.toList());
    }

    /**
     * 获取任务区
     */
    @SuppressWarnings("unchecked")
    public static <T> T getTaskArea(Integer taskAreaId) {
        Map<Integer, TaskArea> taskAreaCache = BaseCache.TASK_AREA_CACHE;
        if (taskAreaCache.containsKey(taskAreaId)) {
            return (T) taskAreaCache.get(taskAreaId);
        }
        return null;
    }

    /**
     * 设置活动地图原点
     */
    public static void setOriginPoint(double x, double y, double z) {
        OriginPoint originPoint = BaseCache.ORIGIN_POINT_CACHE;
        originPoint.setX(x);
        originPoint.setY(y);
        originPoint.setZ(z);
    }

    /**
     * 设置活动地图id
     */
    public static void setActiveMapId(Integer mapId) {
        BaseCache.MAP_ID_CACHE.set(mapId);
    }

    public static Integer getActiveMapId() {
        return BaseCache.MAP_ID_CACHE.get();
    }

    public static OriginPoint getOriginPoint() {
        return BaseCache.ORIGIN_POINT_CACHE;
    }

    /**
     * 判断地图是否加载完成,true为完成
     */
    public static boolean isMapLoadFinish() {
        return BaseCache.MAP_ID_CACHE.get() != 0;
    }

    /***********************************unit 单元**************************************/

    /**
     * 添加调度单元
     */
    public static boolean addUnit(Unit unit) {
        Assert.notNull(unit, "[unit]不能为空");
        Map<Integer, Unit> unitMap = BaseCache.UNIT_CACHE;
        Integer unitId = unit.getUnitId();
        if (unitMap.keySet().contains(unitId)) {
            log.debug("该调度单元[{}]已添加", unitId);
            return false;
        }
        log.debug("新增调度单元,{}", unit);
        BaseCache.UNIT_CACHE.put(unitId, unit);
        unit.updateCache();
        return true;
    }

    /**
     * 删除调度单元
     */
    public static boolean removeUnit(Integer unitId) {
        Map<Integer, Unit> unitMap = BaseCache.UNIT_CACHE;
        if (unitMap.keySet().contains(unitId)) {
            log.debug("删除调度单元[{}]", unitId);
            RedisService.del(BaseConstant.MONITOR_DB, RedisKeyPool.DISPATCH_UNIT + unitId);
            return true;
        }
        log.debug("该调度单元[{}]不存在", unitId);
        return false;
    }

    /**
     * 获取调度单元
     */
    public static Unit getUnit(Integer unitId) {
        Map<Integer, Unit> unitMap = BaseCache.UNIT_CACHE;
        return unitMap.get(unitId);
    }

    public static Collection<Unit> getUnits() {
        Map<Integer, Unit> unitMap = BaseCache.UNIT_CACHE;
        return unitMap.values();
    }

    /******************************** 车辆 *********************************/
    /**
     * 获取车辆
     */
    public static VehicleTask getVehicleTask(Integer vehicleId) {
        Map<Integer, VehicleTask> taskMap = BaseCache.VEHICLE_TASK_CACHE;
        return taskMap.get(vehicleId);
    }

    public static Collection<VehicleTask> getVehicleTasks() {
        Map<Integer, VehicleTask> taskMap = BaseCache.VEHICLE_TASK_CACHE;
        return taskMap.values();
    }

    /**
     * 获取全局路径
     */
    public static GlobalPath getGlobalPath(Integer vehicleId) {
        Map<Integer, GlobalPath> pathCache = BaseCache.VEHICLE_PATH_CACHE;
        return pathCache.get(vehicleId);
    }

    /**
     * 新增全局路径
     */
    public static boolean addGlobalPath(GlobalPath globalPath) {
        if (null != globalPath && globalPath.getVehicleId() > 0) {
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(globalPath.getVehicleId());
            PathManager pathManager = vehicleTask.getHelper().getPathManager();
            if (!pathManager.isValState(PathStateEnum.PATH_CREATING)) {
                log.error("【{}】不是路径生成状态，不添加全局路径!", globalPath.getVehicleId());
                return false;
            }
            if (vehicleTask.getHelper().getInputStateManager().isDefaultInput()) {
                log.error("【{}】为默认输入源，不添加全局路径!", globalPath.getVehicleId());
                return false;
            }
            Map<Integer, GlobalPath> pathCache = BaseCache.VEHICLE_PATH_CACHE;
            if (!pathCache.containsKey(globalPath.getVehicleId())) {
                pathCache.put(globalPath.getVehicleId(), globalPath);
                return true;
            } else {
                log.error("全局路径存在，新增失败!");
            }
        }
        log.error("新增全局路径失败,车辆编号异常!");
        return false;
    }

    /**
     * 删除全局路径
     */
    public static boolean removeGlobalPath(Integer vehicleId) {
        VehicleTask vehicleTask = getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            if (!vehicleTask.isStart()) {
                Map<Integer, GlobalPath> pathCache = BaseCache.VEHICLE_PATH_CACHE;
                if(pathCache.containsKey(vehicleId)){
                    MongoService.updateOne(MongoKeyPool.VEHICLE_GLOBAL_PATH_PREFIX+vehicleId,new Document().append("id",pathCache.get(vehicleId).getId()),new Document().append("endTime",System.currentTimeMillis()));
                    BaseUtil.execTaskAtOnce(MongoKeyPool.MONGO_STORE_REFRESH);
                }
                pathCache.remove(vehicleId);
                RedisService.del(BaseConstant.MONITOR_DB, RedisKeyPool.VAP_PATH_PREFIX + vehicleId);
                removeWorkPathInfo(vehicleId);
                removeVehicleTrail(vehicleId);
                return true;
            }
            log.error("删除全局路径失败，车辆正在运行!");
        }
        return false;
    }

    /**
     * 添加车辆轨迹
     */
    public static boolean addVehicleTrail(VehicleTrail trail) {
        if (null != trail && trail.getVehicleId() > 0) {
            Map<Integer, VehicleTrail> trailCache = BaseCache.VEHICLE_TRAIL_CACHE;
            trailCache.put(trail.getVehicleId(), trail);
            return true;
        }
        log.error("新增车辆轨迹失败,车辆编号异常!");
        return false;
    }

    /**
     * 获取车辆轨迹
     */
    public static VehicleTrail getVehicleTrail(Integer vehicleId) {
        Map<Integer, VehicleTrail> trailCache = BaseCache.VEHICLE_TRAIL_CACHE;
        return trailCache.get(vehicleId);
    }

    /**
     * 删除车辆轨迹
     */
    private static VehicleTrail removeVehicleTrail(Integer vehicleId) {
        Map<Integer, VehicleTrail> trailCache = BaseCache.VEHICLE_TRAIL_CACHE;
        return trailCache.remove(vehicleId);
    }

    /**
     * 初始化车辆路径工作信息
     */
    public static void addWorkPathInfo(WorkPathInfo workPathInfo) {
        Map<Integer, WorkPathInfo> workingPathCache = BaseCache.WORKING_PATH_CACHE;
        if (workPathInfo != null && workPathInfo.getVehicleId() > 0) {
            workingPathCache.put(workPathInfo.getVehicleId(), workPathInfo);
        }
    }

    /**
     * 获取车辆路径信息
     */
    public static WorkPathInfo getWorkPathInfo(Integer vehicleId) {
        Map<Integer, WorkPathInfo> workingPathCache = BaseCache.WORKING_PATH_CACHE;
        return workingPathCache.get(vehicleId);
    }

    private static WorkPathInfo removeWorkPathInfo(Integer vehicleId) {
        Map<Integer, WorkPathInfo> workingPathCache = BaseCache.WORKING_PATH_CACHE;
        return workingPathCache.remove(vehicleId);
    }

    /**
     * 添加定位对象
     */
    public static void addLocation(Location location) {
        if (null != location) {
            Set<Location> objsCache = BaseCache.LOCATION_OBJS_CACHE;
            objsCache.add(location);
        }
    }

    /**
     * 获取定位对象
     */
    public static Set<Location> getLocationOjs() {
        return BaseCache.LOCATION_OBJS_CACHE;
    }

    /**
     * 设置实时对象
     */
    public static void addLiveInfo(Integer id, LiveInfo liveInfo) {
        if (null != liveInfo) {
            BaseCache.DEVICE_LIVE_CACHE.put(id, liveInfo);
        }
    }

    /**
     * 获取对象实时信息
     */
    public static LiveInfo getLiveInfo(Integer id) {
        return BaseCache.DEVICE_LIVE_CACHE.get(id);
    }

    /******************************** 电铲 *********************************/

    /**
     * 新增电铲
     */
    public static synchronized void addExcavator(ExcavatorTask excavatorTask) {
        if (null != excavatorTask && null != excavatorTask.getExcavatorId()) {
            Map<Integer, ExcavatorTask> excavatorInfoCache = BaseCache.EXCAVATOR_INFO_CACHE;
            if (excavatorInfoCache.keySet().contains(excavatorTask.getExcavatorId())) {
                log.debug("该挖掘机编号已添加,{}", excavatorTask.getExcavatorId());
                return;
            }
            log.debug("新增电铲,【{}】", excavatorTask.getExcavatorId());
            excavatorInfoCache.put(excavatorTask.getExcavatorId(), excavatorTask);
        }
    }

    /**
     * 判断电铲是否存在,true存在
     */
    public static boolean isExistExcavator(Integer excavatorId) {
        return null != excavatorId && BaseCache.EXCAVATOR_INFO_CACHE.keySet().contains(excavatorId);
    }

    /**
     * 获取电铲
     */
    public static ExcavatorTask getExcavator(Integer excavatorId) {
        Map<Integer, ExcavatorTask> excavatorInfoCache = BaseCache.EXCAVATOR_INFO_CACHE;
        return excavatorInfoCache.get(excavatorId);
    }

    public static Map<Integer, ExcavatorTask> getExcavatorMap() {
        return BaseCache.EXCAVATOR_INFO_CACHE;
    }


    /******************************** 请求消息 *********************************/

    public static void addResponse(String messageId, Response response) {
        if (null != response && BaseUtil.StringNotNull(messageId)) {
            Map<String, Response> messageCache = BaseCache.RESPONSE_MESSAGE_CACHE;
            messageCache.put(messageId, response);
            return;
        }
        log.error("新增响应消息失败，参数异常!");
    }

    /**
     * 获取响应体
     */
    public static Response getResponseMessage(String messageId) {
        Map<String, Response> messageCache = BaseCache.RESPONSE_MESSAGE_CACHE;
        return messageCache.get(messageId);
    }

    public static void removeResponseMessage(String messageId) {
        BaseCache.RESPONSE_MESSAGE_CACHE.remove(messageId);

    }

    /**
     * 获取请求消息
     */
    public static Request getRequestMessage(String messageId) {
        Map<String, Request> messageCache = BaseCache.REQUEST_MESSAGE_CACHE;
        return messageCache.get(messageId);
    }

    /**
     * 新增消息
     */
    public static void addRequestMessage(Request request) {
        if (null != request && BaseUtil.StringNotNull(request.getMessageId())) {
            Map<String, Request> messageCache = BaseCache.REQUEST_MESSAGE_CACHE;
            messageCache.put(request.getMessageId(), request);
            return;
        }
        log.error("新增请求消息失败，参数异常!");
    }

    /**
     * 添加、获取对象锁
     */
    public static Object objectLock(String key) {
        return BaseCache.OBJECT_LOCK_CACHE.computeIfAbsent(key, entry -> new Object());
    }

    /******************************** 车载故障 *********************************/

    public static void addVehicleTrouble(Map<String, TroubleParse> map) {
        BaseCache.VEHICLE_TROUBLE_CACHE.putAll(map);
    }

    /**
     * 获取故障实体
     */
    public static TroubleParse getTroubleEntry(String key) {
        return BaseCache.VEHICLE_TROUBLE_CACHE.get(key);
    }
}
