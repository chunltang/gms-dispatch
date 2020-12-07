package com.baseboot.service.calculate;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.CollisionDetectUtil;
import com.baseboot.entry.dispatch.CalculatedValue;
import com.baseboot.entry.dispatch.monitor.Location;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.dispatch.path.GlobalPath;
import com.baseboot.entry.dispatch.path.Vertex;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.entry.map.Point;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 位置碰撞计算
 */
@Slf4j
@CalculateClass
@Component
public class CalculateAbsoluteDistance implements Calculate {

    @Override
    public CalculateResult calculateEndPoint(Integer vehicleId, Set<Location> locations) {
        CalculateResult result = new CalculateResult();
        result.setType(getCalculateType());
        result.setTargetId(vehicleId);
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        Point curLocation = vehicleTask.getCurLocation();
        GlobalPath globalPath = BaseCacheUtil.getGlobalPath(vehicleId);
        if (BaseUtil.allObjNotNull(curLocation, globalPath)) {
            WorkPathInfo workPathInfo = globalPath.getWorkPathInfo();
            CollisionDetectUtil.Rectangle rectangle = vehicleTask.getOutline(CalculatedValue.COLLISION_DETECT_DISTANCE);
            List<Vertex> vertexs = globalPath.getVertexs();
            int endId = CalculatedValue.COLLISION_POINT_NUMS + workPathInfo.getNearestId();
            int index = 1000000;
            int sourceId = vehicleId;
            for (Location location : locations) {
                if (!location.getUniqueId().equals(vehicleId)) {
                    for (int i = workPathInfo.getNearestId();
                         i <= (endId < workPathInfo.getSectionPathEndId() ? endId : workPathInfo.getSectionPathEndId()); i += 3) {
                        rectangle.setCenterX(vertexs.get(i).getX());
                        rectangle.setCenterY(vertexs.get(i).getY());
                        rectangle.setAngle(vertexs.get(i).getDirection());
                        Boolean detect = CollisionDetectUtil.collisionDetectByAngle(location.getOutline(CalculatedValue.COLLISION_DETECT_DISTANCE),
                                rectangle);
                        if (index > i - 30 && detect) {
                            log.warn("【{}】和{}碰撞检测不通过,indexId={}",
                                    vehicleId, location.getUniqueId(), i - 30);
                            index = i - 30;//可修改这个值设置碰撞停车距离,这里为点数
                            sourceId = location.getUniqueId();
                            break;
                        }
                    }
                }
            }
            if (index < 0) {
                index = 0;
            }
            result.setIndex(index);
            result.setSourceId(sourceId);
        }
        return result;
    }

    @Override
    public CalculateTypeEnum getCalculateType() {
        return CalculateTypeEnum.VEHICLE_ABSOLUTE_DISTANCE_LIMITING;
    }
}
