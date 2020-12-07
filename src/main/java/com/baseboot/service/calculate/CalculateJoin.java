package com.baseboot.service.calculate;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.ClassUtil;
import com.baseboot.entry.dispatch.monitor.Location;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.entry.map.Point;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 异步计算获取最短终点路径编号
 */
@Slf4j
public class CalculateJoin {

    private final static Map<Class<? extends Calculate>, ? super Calculate> calculateMap = new HashMap<>();

    /**
     * 初始化所有计算对象
     * */
    static {
        List<Class> allClassByAnnotation = ClassUtil.getAllClassByAnnotation(CalculateClass.class);
        for (Class aClass : allClassByAnnotation) {
            try {
                Object instance = aClass.newInstance();
                calculateMap.put(aClass, (Calculate) instance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取最短位置索引
     */
    public static int getJoinMinId(Integer vehicleId) {
        int index = 100000;
        List<CalculateResult> results = new ArrayList<>();
        Set<Location> locations = inWarnRangeLocation(vehicleId);
        for (Object obj : calculateMap.values()) {
            CalculateResult calculateResult = ((Calculate) obj).calculateEndPoint(vehicleId, locations);
            results.add(calculateResult);
        }

        CalculateResult printResult = null;
        for (CalculateResult result : results) {
            if (index > result.getIndex()) {
                index = result.getIndex();
                printResult = result;
            }
        }

        WorkPathInfo workPathInfo = BaseCacheUtil.getWorkPathInfo(vehicleId);
        if (index == 100000) {
            index = 0;
        } else {
            log.warn("【{}】被{}限制，限制类型:{},trailId={},oldTrailId={}",
                    printResult.getTargetId(), printResult.getSourceId(), printResult.getType().getDesc(), index, workPathInfo.getTrailEndId());
        }

        return index;
    }


    /**
     * 预警范围内的定位对象
     */
    private static Set<Location> inWarnRangeLocation(Integer vehicleId) {
        Set<Location> objs = new HashSet<>();
        Set<Location> locationOjs = BaseCacheUtil.getLocationOjs();
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null == vehicleTask) {
            return objs;
        }
        for (Location location : locationOjs) {
            Point location1 = location.getCurLocation();
            Point location2 = vehicleTask.getCurLocation();
            if (BaseUtil.allObjNotNull(location1, location2)) {
                double dis = DispatchUtil.twoPointDistance(location1, location2);
                if (dis < CalculateConfig.WARN_RANGE) {
                    objs.add(location);
                }
            }
        }
        return objs;
    }
}
