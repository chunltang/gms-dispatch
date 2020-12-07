package com.baseboot.service.calculate;

import com.baseboot.entry.dispatch.monitor.Location;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.dispatch.path.GlobalPath;
import com.baseboot.entry.global.BaseCache;
import com.baseboot.service.BaseCacheUtil;

import java.util.*;

/**
 * 移动授权终点
 */
public interface Calculate {

    /**
     * 获取终点路径点编号索引
     */
    CalculateResult calculateEndPoint(Integer vehicleId, Set<Location> locations);

    CalculateTypeEnum getCalculateType();

    /**
     * 获取对应类型的定位对象
     */
    default <T extends Location> Set<T> getLocationByClass(Set<Location> locations, Class<T> clazz) {
        Set<T> clazzObjs = new HashSet<>();
        for (Location location : locations) {
            if (location.getClass().equals(clazz)) {
                clazzObjs.add((T) location);
            }
        }
        return clazzObjs;
    }

    /**
     * 获取指定编号的位置对象
     */
    default <T extends Location> T getLocationByUniqueId(Set<Location> locations, Integer uniqueId) {
        for (Location location : locations) {
            if (location.getUniqueId().equals(uniqueId)) {
                return (T)location;
            }
        }
        return null;
    }

    /**
     * 获取系统中所有车
     */
    default Collection<VehicleTask> getSystemAllVehicles() {
        return BaseCache.VEHICLE_TASK_CACHE.values();
    }

    /**
     * 获取系统中所有具有定位信息的对象
     */
    default Set<Location> getSysAllLocations() {
        return BaseCacheUtil.getLocationOjs();
    }

    /**
     * 获取所有正在运行的矿车的路径
     */
    default Collection<GlobalPath> getVehicleGlobalPaths() {
        Collection<VehicleTask> vehicles = getSystemAllVehicles();
        List<GlobalPath> globalPaths = new ArrayList<>();
        Iterator<VehicleTask> iterator = vehicles.iterator();
        while (iterator.hasNext()) {
            VehicleTask next = iterator.next();
            if (next.isStart()) {
                GlobalPath path = BaseCacheUtil.getGlobalPath(next.getVehicleId());
                if (null != path) {
                    globalPaths.add(path);
                }
            }
        }
        return globalPaths;
    }
}
