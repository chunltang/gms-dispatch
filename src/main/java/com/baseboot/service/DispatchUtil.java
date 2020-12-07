package com.baseboot.service;

import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleLiveInfo;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.dispatch.path.GlobalPath;
import com.baseboot.entry.dispatch.path.Path;
import com.baseboot.entry.dispatch.path.Vertex;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.entry.global.BaseCache;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.IEnum;
import com.baseboot.entry.global.RedisKeyPool;
import com.baseboot.entry.map.IdPoint;
import com.baseboot.entry.map.Point;
import com.baseboot.entry.map.SemiStatic;
import com.baseboot.enums.AreaTypeEnum;
import com.baseboot.service.dispatch.input.DispatchInput;
import com.baseboot.service.dispatch.input.InputCache;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DispatchUtil {

    /**
     * 获取活动地图id
     */
    public static Integer getActivateMapId() {
        String str = RedisService.get(BaseConstant.KEEP_DB, RedisKeyPool.ACTIVITY_MAP);
        if (BaseUtil.StringNotNull(str)) {
            return Integer.valueOf(str);
        }
        log.error("获取活动地图id失败!");
        return null;
    }

    /**
     * 计算一个点在指定路径指定段中的最近距离
     */
    public static int getNearestId(Point targetPoint, Path path, int startId, int endId) {
        if (BaseUtil.allObjNotNull(targetPoint, path) && path.getVertexNum() > 0) {
            List<Vertex> vertexs = path.getVertexs();
            endId = vertexs.size() - 1 > endId ? endId : vertexs.size() - 1;
            if (startId > endId) {
                log.error("#getNearestId:车辆[{}]索引异常,startId={},endId={}", path.getVehicleId(), startId, endId);
                return endId;
            }
            int i = startId;
            int resultId = startId;
            double resultDis = 1000000;
            for (; i < endId; i += 2) {
                double dis = twoPointDistance(targetPoint.getX(), targetPoint.getY(), vertexs.get(i).getX(), vertexs.get(i).getY());
                if (dis < resultDis) {
                    resultDis = dis;
                    resultId = i;
                }
            }
            return resultId;
        }
        return startId;
    }

    /**
     * 计算两点间的距离
     */
    public static double twoPointDistance(Point p1, Point p2) {
        double xd = Math.pow(p1.getX() - p2.getX(), 2);
        double yd = Math.pow(p1.getY() - p2.getY(), 2);
        return Math.sqrt(xd + yd);
    }

    public static double twoPointDistance(double x1, double y1, double x2, double y2) {
        double xd = Math.pow(x1 - x2, 2);
        double yd = Math.pow(y1 - y2, 2);
        return Math.sqrt(xd + yd);
    }

    /**
     * 获取两个索引间的距离
     */
    public static double GetDistance(Path path, int startId, int endId) {
        if (null != path && startId <= endId && path.getVertexNum() > 0) {
            List<Vertex> vertexs = path.getVertexs();
            endId = vertexs.size() - 1 > endId ? endId : vertexs.size() - 1;
            if (BaseUtil.CollectionNotNull(vertexs)) {
                return Math.abs(vertexs.get(endId).getS() - vertexs.get(startId).getS());
            }
        }
        if (startId > endId) {
            if (path instanceof GlobalPath) {
                log.error("#GetDistance:车辆[{}]索引异常,startId={},endId={},trailId={},nearsetId={},nums={}",
                        path.getVehicleId(), startId, endId, ((GlobalPath) path).getWorkPathInfo().getTrailEndId(), ((GlobalPath) path).getWorkPathInfo().getNearestId(), path.getVertexNum());
            } else {
                log.error("#GetDistance:车辆[{}]索引异常,startId={},endId={}",
                        path.getVehicleId(), startId, endId);
            }

            return 0;
        }
        return 0;
    }

    /**
     * 判断两个车的运行趋势，远离还是接近
     *
     * @return true接近，false远离
     */
    public static boolean runningTendency() {
        return false;
    }

    /**
     * 获取车辆所有运行状态
     */
    public static void sendVehicleInfo(Integer vehicleId) {
        Map<String, Object> infos = new HashMap<>();
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        infos.put("vehicleId", vehicleId);//车辆id
        infos.put("dispatchState", vehicleTask.getHelper().getDispatchStateManager().getDispatchState().getDesc());//调度状态
        DispatchInput input = InputCache.getDispatchInput(vehicleId);
        if (null != input && null != input.getTaskState()) {
            infos.put("taskState", ((IEnum) input.getTaskState()).getDesc());//任务状态
        }
        infos.put("taskCode", vehicleTask.getHelper().getVehicleMonitorManager().getTaskCode().getDesc());//vak指令
        infos.put("vakMode", vehicleTask.getHelper().getVehicleMonitorManager().getCurModeState().getDesc());//控制模式
        infos.put("isStart", vehicleTask.getHelper().getRunStateManager().isStart());//是否允许运行
        infos.put("isRunning", vehicleTask.getHelper().getRunStateManager().isRunning());//是否下发轨迹
        infos.put("curPoint", vehicleTask.getCurLocation().toString());//车辆位置
        infos.put("curSpeed", vehicleTask.getHelper().getCurSpeed());//车辆速度
        infos.put("pathState", vehicleTask.getHelper().getPathManager().getPathState().getDesc());//车辆路径状态
        SemiStatic curArea = vehicleTask.getHelper().getCurArea();
        if (null != curArea) {
            infos.put("curArea", vehicleTask.getHelper().getCurArea().getName());//车辆区域位置
        }
        Point endPoint = InputCache.getEndPoint(vehicleId);
        infos.put("endPoint", null != endPoint ? endPoint.toString() : "");//车辆终点位置
        Integer areaId = vehicleTask.getHelper().getLiveInfo().getTaskAreaId();
        if (null != areaId) {
            SemiStatic semiStatic = BaseCache.SEMI_STATIC_CACHE.get(areaId);
            if (null != semiStatic) {
                infos.put("endArea", semiStatic.getName());//车辆终点区域
            }
        }
        GlobalPath globalPath = BaseCacheUtil.getGlobalPath(vehicleId);
        if (null != globalPath) {
            WorkPathInfo workPathInfo = globalPath.getWorkPathInfo();
            infos.put("pathCount", globalPath.getVertexNum());//全局路径总点数
            infos.put("pathNearestId", workPathInfo.getNearestId());//最近点id
            VehicleLiveInfo liveInfo = (VehicleLiveInfo) BaseCacheUtil.getLiveInfo(vehicleId);
            infos.put("startDistance", liveInfo.getNowDistance());//起点距离
            infos.put("endDistance", liveInfo.getEndDistance() - liveInfo.getNowDistance());//终点距离
            Double deviation = vehicleTask.getHelper().getLiveInfo().getPathDeviation();
            if (null != deviation) {
                infos.put("deviation", BaseUtil.getDoubleScale(deviation, 4));//路径偏差
            }
        }
        RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.REDIS_TEST_PREFIX + vehicleId, BaseUtil.toJson(infos));
    }

    public static SemiStatic isInnerArea(Point target) {
        Map<Integer, SemiStatic> semiStaticCache = BaseCache.SEMI_STATIC_CACHE;
        SemiStatic result = null;
        for (SemiStatic semiStatic : semiStaticCache.values()) {
            if (BaseUtil.CollectionNotNull(semiStatic.getBorder()) && isInnerArea(target, semiStatic.getBorder())) {
                result = semiStatic;
                break;
            }
        }
        return result;
    }

    /**
     * 区域筛选
     */
    public static SemiStatic isInnerArea(Point target, AreaTypeEnum type) {
        Map<Integer, SemiStatic> semiStaticCache = BaseCache.SEMI_STATIC_CACHE;
        SemiStatic result = null;
        for (SemiStatic semiStatic : semiStaticCache.values()) {
            if (semiStatic.getAreaType().equals(type)) {
                if (BaseUtil.CollectionNotNull(semiStatic.getBorder()) && isInnerArea(target, semiStatic.getBorder())) {
                    result = semiStatic;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 判断点是否在一个区域内
     */
    public static boolean isInnerArea(Point target, List<IdPoint> borderPoint) {
        List<IdPoint> points = BaseUtil.sparseList(borderPoint, 5);//点稀疏
        int iSum, iCount, iIndex;
        double y1 = 0, y2 = 0, x1 = 0, x2 = 0, ym;
        if (points.size() < 3) {
            return false;
        }
        iSum = 0;
        iCount = points.size();
        for (iIndex = 0; iIndex < iCount; iIndex ++) {
            if (iIndex == iCount - 1) {
                x1 = points.get(iIndex).getX();
                y1 = points.get(iIndex).getY();
                x2 = points.get(0).getX();
                y2 = points.get(0).getY();
            } else {
                x1 = points.get(iIndex).getX();
                y1 = points.get(iIndex).getY();
                x2 = points.get(iIndex + 1).getX();
                y2 = points.get(iIndex + 1).getY();
            }
            double x = target.getX();
            double y = target.getY();
            // 以下语句判断A点是否在边的两端点的水平平行线之间，在则可能有交点，开始判断交点是否在左射线上  
            if (((x >= x1) && (x < x2)) || ((x >= x2) && (x < x1))) {
                if (Math.abs(x1 - x2) > 0) {
                    //得到 A点向左射线与边的交点的x坐标：  
                    ym = y1 - ((y1 - y2) * (x1 - x)) / (x1 - x2);
                    // 如果交点在A点左侧（说明是做射线与 边的交点），则射线与边的全部交点数加一：
                    if (ym == y) {
                        return true;
                    }
                    if (ym < y) {
                        iSum++;
                    }
                }
            }
        }
        return (iSum % 2) != 0;
    }

}

