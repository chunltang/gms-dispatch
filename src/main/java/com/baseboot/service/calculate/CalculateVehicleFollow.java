package com.baseboot.service.calculate;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.CollisionDetectUtil;
import com.baseboot.common.utils.CollisionDetectUtil.Rectangle;
import com.baseboot.entry.dispatch.CalculatedValue;
import com.baseboot.entry.dispatch.monitor.Location;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.dispatch.path.GlobalPath;
import com.baseboot.entry.dispatch.path.Vertex;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 计算车辆跟随时的终点索引(同一条路，前面有车)
 */
@Slf4j
//@CalculateClass
@Component
public class CalculateVehicleFollow implements Calculate {

    @Override
    public CalculateResult calculateEndPoint(Integer vehicleId, Set<Location> locations) {
        CalculateResult result = new CalculateResult();
        result.setTargetId(vehicleId);
        result.setType(getCalculateType());
        GlobalPath globalPath = BaseCacheUtil.getGlobalPath(vehicleId);
        WorkPathInfo workPathInfo = BaseCacheUtil.getWorkPathInfo(vehicleId);
        if (null == globalPath || null == workPathInfo) {
            return result;
        }
        int nearestId = workPathInfo.getNearestId();
        int sectionPathEndId = workPathInfo.getSectionPathEndId();
        int trailEndId = workPathInfo.getTrailEndId();
        if (nearestId >= sectionPathEndId) {
            return result;
        }
        Set<VehicleTask> vehicleTaskLoc = getLocationByClass(locations, VehicleTask.class);
        List<Vertex> vertexs = globalPath.getVertexs();
        //离路径最近的车
        nearestPathVehicle(result, vehicleId, workPathInfo, vehicleTaskLoc, vertexs);
        if (null != result.getSourceId()) {
            VehicleTask otherTask = getLocationByUniqueId(locations, result.getSourceId());
            if (null == otherTask || null == otherTask.getCurLocation() || !BaseUtil.CollectionNotNull(vertexs)) {
                return result;
            }
            log.warn("【{}】跟随获取的碰撞点为:{}", vehicleId, result.getIndex());
            //计算位置点在危险范围外的最小点
            for (int i = nearestId; i < result.getIndex(); i += 3) {
                double distance = DispatchUtil.GetDistance(globalPath, i, result.getIndex());
                double len = otherTask.getHelper().getRunStateManager().isRunning() ? otherTask.followSafeDistance() : 0;
                if (distance < (len > 0 ? len : (otherTask.stopSafePADistance() + CalculatedValue.COLLISION_DETECT_DISTANCE) * 2)) {//和前车的距离
                    if (i == nearestId) {//平滑过渡
                        result.setIndex(nearestId + (int) (0.1 * Math.abs(trailEndId - nearestId)));
                    } else if (result.getIndex() < trailEndId) {
                        result.setIndex(result.getIndex());
                        break;
                    } else {
                        result.setIndex(i);
                    }
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 计算在路径上并且离车最近那台,只有在路径上的车，并且在轨迹范围内，才跟随行驶
     * 计算结果为：这个车在我路径上，并且离我最近
     */
    private void nearestPathVehicle(CalculateResult result, Integer vehicleId, WorkPathInfo workPathInfo, Set<VehicleTask> vehicleTaskLoc, List<Vertex> vertexs) {
        Integer otherId = null;
        if (null != workPathInfo && BaseUtil.CollectionNotNull(vehicleTaskLoc) && BaseUtil.CollectionNotNull(vertexs)) {
            int nearestId = workPathInfo.getNearestId();
            VehicleTask curTask = BaseCacheUtil.getVehicleTask(vehicleId);

            int index = vertexs.size();
            Rectangle curRec = new Rectangle(0, 0, curTask.stopSafePADistance() * 2, curTask.stopSafeRLDistance() * 2, 0);
            for (VehicleTask location : vehicleTaskLoc) {
                Rectangle otherRec = location.getOutline(CalculatedValue.COLLISION_DETECT_DISTANCE);
                if (!vehicleId.equals(location.getUniqueId())) {
                    for (int i = nearestId; i < vertexs.size(); i += 3) {
                        Vertex vertex = vertexs.get(i);
                        curRec.setCenterX(vertex.getX());
                        curRec.setCenterY(vertex.getY());
                        curRec.setAngle(vertex.getDirection());
                        if (Math.abs(vertex.getDirection() - otherRec.getAngle()) < 90 &&
                                index >= i &&
                                CollisionDetectUtil.collisionDetectByAngle(otherRec, curRec)) {
                            index = i;
                            otherId = location.getUniqueId();
                            break;
                        }
                    }
                }
            }
            result.setIndex(index);
        }
        result.setSourceId(otherId);
    }

    /**
     * 如果碰撞点辆车的角度差小于10，距离小于于3，表示是同向路线，不做针用区限制
     */
    private boolean isIgnore(Vertex ver1, Vertex ver2) {
        return Math.abs(ver1.getDirection() - ver2.getDirection()) > 90;
    }

    @Override
    public CalculateTypeEnum getCalculateType() {
        return CalculateTypeEnum.VEHICLE_PATH_POSITION_LIMITING;
    }
}
