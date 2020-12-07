package com.baseboot.service.calculate;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.CollisionDetectUtil;
import com.baseboot.common.utils.CollisionDetectUtil.Rectangle;
import com.baseboot.entry.dispatch.monitor.Location;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.dispatch.path.GlobalPath;
import com.baseboot.entry.dispatch.path.Vertex;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.entry.global.MongoKeyPool;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.dispatch.task.MongoStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 争用区计算,距离近先行
 */
@Slf4j
@CalculateClass
@Component
public class CalculateDisputeArea implements Calculate {

    private final static AtomicLong DISPUTE_AREA_ID = new AtomicLong(0);
    /**
     * 所有争用区
     */
    private final static Set<DisputeArea> DISPUTE_AREAS = Collections.synchronizedSet(new HashSet<DisputeArea>());

    @Override
    public CalculateResult calculateEndPoint(Integer vehicleId, Set<Location> locations) {
        clearExpirationDisputeArea();
        GlobalPath globalPath = BaseCacheUtil.getGlobalPath(vehicleId);
        WorkPathInfo workPathInfo = BaseCacheUtil.getWorkPathInfo(vehicleId);
        CalculateResult result = new CalculateResult();
        result.setType(getCalculateType());
        result.setTargetId(vehicleId);
        if (null == globalPath || null == workPathInfo) {
            return result;
        }
        calculateDisputeArea(globalPath, vehicleId, locations);
        int nearestId = workPathInfo.getNearestId();
        int sectionPathEndId = workPathInfo.getSectionPathEndId();
        int trailEndId = workPathInfo.getTrailEndId();

        if (nearestId > sectionPathEndId) {
            return result;
        }
        List<Vertex> vertexs = globalPath.getVertexs();
        //计算最近的争用区
        DisputeArea nearestDisputeArea = null;
        int id = 1000000;//最近争用区距离
        int otherId = 100000;
        DisputeArea otherDisputeArea = null;
        Iterator<DisputeArea> iterator = DISPUTE_AREAS.iterator();
        while (iterator.hasNext()) {
            DisputeArea disputeArea = iterator.next();
            if (disputeArea.getUseObjects().contains(vehicleId)) {//过滤
                Integer index = disputeArea.getIndexMap().get(vehicleId);
                if (disputeArea.isLock()
                        && null != index && index < otherId &&
                        !vehicleId.equals(disputeArea.getUniqueId())) {
                    otherId = index;
                    otherDisputeArea = disputeArea;
                    log.error("【{}】存在其他车的限制,{}", vehicleId, disputeArea.getUniqueId());
                }
                if (null != index && index < id) {
                    id = index;
                    nearestDisputeArea = disputeArea;
                }
            }
        }

        if (null != nearestDisputeArea) {
            log.debug("【争用区】:主体车{}的最近争用区为{},indexs={},lockId={}", vehicleId, nearestDisputeArea.getId(), nearestDisputeArea.getIndexMap(), nearestDisputeArea.getUniqueId());
            if (DispatchUtil.GetDistance(globalPath, nearestId, id > nearestId ? id : nearestId + 1) < CalculateConfig.DISPUTE_RANGE) {//控制距离，在距离范围内才判断争用区限制
                studyLockState(nearestDisputeArea);
                if (!nearestDisputeArea.isLock()) {//没有被锁定
                    //根据规则，判断哪个车锁定区域
                    lockDisputeArea(nearestDisputeArea, vehicleId);
                    if (vehicleId.equals(nearestDisputeArea.getUniqueId())) {//判断是不是当前车锁定
                        if (trailEndId > otherId && null != otherDisputeArea) {
                            result.setIndex(otherId);
                            result.setSourceId(otherDisputeArea.getUniqueId());
                            double distance = DispatchUtil.twoPointDistance(vertexs.get(nearestId).getX(), vertexs.get(nearestId).getY(), otherDisputeArea.getX(), otherDisputeArea.getY());
                            log.warn("【争用区】:非最近限制运行,当前车[{}],争用区被车辆[{}]锁定,id={},calculateId={},indexs={},dis={},nearestId={},trailEndId={}",
                                    vehicleId, otherDisputeArea.getUniqueId(), otherDisputeArea.getId(), otherId, otherDisputeArea.getIndexMap(), distance, nearestId, trailEndId);
                        } else {
                            log.debug("【争用区】:继续运行,当前车[{}],争用区已被{}锁定,id={}", vehicleId, nearestDisputeArea.getUniqueId(), nearestDisputeArea.getId());
                        }
                        return result;
                    }
                }
                //最近争用区被锁定，计算停车终点
                Integer index = nearestDisputeArea.getIndexMap().get(vehicleId);
                if (index < nearestId) {
                    log.warn("【{}】已经过的最近点", vehicleId);
                    return result;
                }
                for (int i = nearestId; i < index; i += 2) {
                    double distance = DispatchUtil.GetDistance(globalPath, i, index);
                    if (distance < CalculateConfig.DISPUTE_DISTANCE) {
                        if (nearestDisputeArea.isLock()) {//争用区被锁定，停在争用区外面
                            if (!nearestDisputeArea.getUniqueId().equals(vehicleId)) {//锁定对象不是自己
                                if (i == nearestId) {
                                    result.setIndex(nearestId + (int) (0.2 * Math.abs(trailEndId - nearestId)));
                                } else if (index <= trailEndId) {
                                    result.setIndex(index);
                                } else {
                                    result.setIndex(i);
                                }

                                result.setSourceId(nearestDisputeArea.getUniqueId());
                                log.warn("【争用区】:限制运行,当前车[{}],争用区被车辆[{}]锁定,id={},calculateId={},indexs={},dis={},nearestId={},trailEndId={}",
                                        vehicleId, nearestDisputeArea.getUniqueId(), nearestDisputeArea.getId(), i, nearestDisputeArea.getIndexMap(), distance, nearestId, trailEndId);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 锁定状态学习
     */
    private void studyLockState(DisputeArea nearestDisputeArea) {
        Integer uniqueId = null;
        Iterator<DisputeArea> iterator = DISPUTE_AREAS.iterator();
        while (iterator.hasNext()) {
            DisputeArea disputeArea = iterator.next();
            if (disputeArea.getUseObjects().containsAll(nearestDisputeArea.getUseObjects())) {
                uniqueId = disputeArea.getUniqueId();
                if (null != uniqueId) {
                    break;
                }
            }
        }
        if (null != uniqueId) {
            while (iterator.hasNext()) {
                DisputeArea disputeArea = iterator.next();
                if (disputeArea.getUseObjects().containsAll(nearestDisputeArea.getUseObjects())) {
                    disputeArea.setUniqueId(uniqueId);
                }
            }
        }
    }

    /**
     * 判断规则，那辆车锁定区域
     */
    private void lockDisputeArea(DisputeArea nearestDisputeArea, Integer curVehicleId) {
        if (nearestDisputeArea.getUseObjects().size() == 2) {
            Integer id1 = nearestDisputeArea.getUseObjects().get(0);
            Integer id2 = nearestDisputeArea.getUseObjects().get(1);
            Integer otherId = id1.equals(curVehicleId) ? id2 : id1;
            VehicleTask vehicleTask1 = BaseCacheUtil.getVehicleTask(curVehicleId);
            VehicleTask vehicleTask2 = BaseCacheUtil.getVehicleTask(otherId);
            if (BaseUtil.allObjNotNull(vehicleTask1, vehicleTask2)) {
                //如果当前车的终点是争用区，让另外一辆车先走
                DisputeArea disputeArea = getOtherDisputeArea(otherId, curVehicleId);
                if (null == disputeArea) {
                    return;
                }
                Integer index1 = nearestDisputeArea.getIndexMap().get(curVehicleId);
                Integer index2 = disputeArea.getIndexMap().get(otherId);
                WorkPathInfo workPathInfo1 = BaseCacheUtil.getWorkPathInfo(curVehicleId);
                WorkPathInfo workPathInfo2 = BaseCacheUtil.getWorkPathInfo(otherId);
                if (!BaseUtil.allObjNotNull(workPathInfo1, workPathInfo2)) {
                    log.error("【{}】争用区加锁数据异常", curVehicleId);
                    return;
                }

                //当前车的位置为争用区，当前车先走
                /*if (index1 - workPathInfo1.getNearestId() > 0 && index1 - workPathInfo1.getNearestId() < 30) {
                    log.warn("【{}】争用区进入位置为争用区判断锁定", curVehicleId);
                    setLock(curVehicleId, curVehicleId, otherId);
                    return;
                }*/

                double dis1 = DispatchUtil.GetDistance(workPathInfo1.getPath(), workPathInfo1.getNearestId(), index1 > workPathInfo1.getNearestId() ? index1 : workPathInfo1.getNearestId() + 1);
                double dis2 = DispatchUtil.GetDistance(workPathInfo2.getPath(), workPathInfo2.getNearestId(), index2 > workPathInfo2.getNearestId() ? index2 : workPathInfo2.getNearestId() + 1);
                log.warn("【{}】距争用区距离：{}，[{}]距争用区距离：{}", curVehicleId, dis1, otherId, dis2);
                if ((dis1 > 0 && dis1 < 15 && dis2 > 0 && dis2 < 15)) {
                    log.warn("【{}】争用区进入速度判断锁定", curVehicleId);
                    //如果距离相差小，则判断速度
                    if (vehicleTask1.getHelper().getCurSpeed() > vehicleTask2.getHelper().getCurSpeed() && vehicleTask2.getHelper().getCurSpeed() < 0.5) {
                        setLock(curVehicleId, curVehicleId, otherId);
                        return;
                    } else if (vehicleTask1.getHelper().getCurSpeed() < vehicleTask2.getHelper().getCurSpeed() && vehicleTask1.getHelper().getCurSpeed() < 0.5) {
                        setLock(otherId, curVehicleId, otherId);
                        return;
                    }
                }

                if (dis1 > 0 && dis1 < 15 && dis1 < dis2) {
                    log.warn("【{}】争用区进入争用区起点判断锁定", curVehicleId);
                    setLock(curVehicleId, curVehicleId, otherId);
                    return;
                } else if (dis2 > 0 && dis2 < 15 && dis2 < dis1) {
                    log.warn("【{}】争用区进入争用区起点判断锁定", curVehicleId);
                    setLock(otherId, curVehicleId, otherId);
                    return;
                }

                //重载判断，在装载区
                if (vehicleTask1.getHelper().isInnerLoadArea() && vehicleTask1.getHelper().getDispatchStateManager().isDispatchLoadState()
                        && vehicleTask2.getHelper().getDispatchStateManager().isDispatchNoLoadState()) {
                    log.warn("【{}】争用区进入装卸判断锁定", curVehicleId);
                    setLock(curVehicleId, curVehicleId, otherId);
                    return;
                } else if (vehicleTask2.getHelper().isInnerLoadArea() && vehicleTask2.getHelper().getDispatchStateManager().isDispatchLoadState()
                        && vehicleTask1.getHelper().getDispatchStateManager().isDispatchNoLoadState()) {
                    log.warn("【{}】争用区进入装卸判断锁定", curVehicleId);
                    setLock(otherId, curVehicleId, otherId);
                    return;
                    //在卸载区
                } else if (vehicleTask1.getHelper().isInnerUnloadArea() && vehicleTask1.getHelper().getDispatchStateManager().isDispatchNoLoadState()
                        && vehicleTask2.getHelper().getDispatchStateManager().isDispatchLoadState()) {
                    log.warn("【{}】争用区进入装卸判断锁定", curVehicleId);
                    setLock(curVehicleId, curVehicleId, otherId);
                    return;
                } else if (vehicleTask2.getHelper().isInnerUnloadArea() && vehicleTask2.getHelper().getDispatchStateManager().isDispatchNoLoadState()
                        && vehicleTask1.getHelper().getDispatchStateManager().isDispatchLoadState()) {
                    log.warn("【{}】争用区进入装卸判断锁定", curVehicleId);
                    setLock(otherId, curVehicleId, otherId);
                    return;
                }


                if ((vehicleTask1.getHelper().isInnerWorkingArea() || vehicleTask2.getHelper().isInnerWorkingArea())
                        && (vehicleTask1.getHelper().getDispatchStateManager().isDispatchLoadState() ||
                        vehicleTask2.getHelper().getDispatchStateManager().isDispatchLoadState())) {
                    log.warn("【{}】争用区进入速度判断锁定", curVehicleId);
                    if (vehicleTask1.getHelper().getDispatchStateManager().isDispatchLoadState()) {
                        setLock(curVehicleId, curVehicleId, otherId);
                        return;
                    } else if (vehicleTask2.getHelper().getDispatchStateManager().isDispatchLoadState()) {
                        setLock(otherId, curVehicleId, otherId);
                        return;
                    }
                }

                log.warn("【{}】争用区进入距离判断锁定", curVehicleId);
                if (index1 - workPathInfo1.getNearestId() >= index2 - workPathInfo2.getNearestId()) {
                    setLock(otherId, curVehicleId, otherId);
                } else {
                    setLock(curVehicleId, curVehicleId, otherId);
                }
            }
        }
    }

    /**
     * 计算俩车的最近争用区
     */
    private DisputeArea getOtherDisputeArea(Integer otherId, Integer curVehicleId) {
        GlobalPath globalPath1 = BaseCacheUtil.getGlobalPath(otherId);
        GlobalPath globalPath2 = BaseCacheUtil.getGlobalPath(curVehicleId);
        VehicleTask otherTask = BaseCacheUtil.getVehicleTask(otherId);
        VehicleTask curTask = BaseCacheUtil.getVehicleTask(curVehicleId);
        if (BaseUtil.allObjNotNull(globalPath1, globalPath2)) {
            List<Vertex> vertexs1 = globalPath1.getVertexs();
            List<Vertex> vertexs2 = globalPath2.getVertexs();
            int nearestId1 = globalPath1.getWorkPathInfo().getNearestId();
            int nearestId2 = globalPath2.getWorkPathInfo().getNearestId();
            int pointNum2 = globalPath2.getWorkPathInfo().getPathPointNum();
            int sectionPathEndId = globalPath1.getWorkPathInfo().getSectionPathEndId();
            //计算最近争用区
            Rectangle curRec = new Rectangle(0, 0, curTask.stopSafePADistance() * 2, curTask.stopSafeRLDistance() * 2, 0);
            Rectangle otherRec = new Rectangle(0, 0, otherTask.stopSafePADistance() * 2, otherTask.stopSafeRLDistance() * 2, 0);
            for (int i = nearestId1; i < sectionPathEndId; i += 3) {
                for (int j = nearestId2; j < pointNum2; j += 3) {
                    double x = vertexs1.get(i).getX();
                    double y = vertexs1.get(i).getY();
                    curRec.setCenterX(vertexs2.get(j).getX());
                    curRec.setCenterY(vertexs2.get(j).getY());
                    curRec.setAngle(vertexs2.get(j).getDirection());

                    otherRec.setCenterX(vertexs1.get(i).getX());
                    otherRec.setCenterY(vertexs1.get(i).getY());
                    otherRec.setAngle(vertexs1.get(i).getDirection());

                    //碰撞检测
                    if (CollisionDetectUtil.collisionDetectByAngle(curRec, otherRec)) {
                        Map<Integer, Integer> indexMap = new HashMap<>();
                        indexMap.put(otherId, i);
                        indexMap.put(curVehicleId, j);
                        log.warn("【{}】计算辆车最近争用区，进入碰撞检测,index={}", curVehicleId, indexMap);
                        return addDisputeArea(otherId, indexMap, x, y);
                    }

                    double s = DispatchUtil.GetDistance(globalPath2, nearestId2, j);
                    if (s > CalculateConfig.DISPUTE_RANGE) {//只计算预警范围内的争用区
                        break;
                    }
                }
            }
        }
        log.error("【{}】计算俩车的最近争用区数据异常,otherId={}", curVehicleId, otherId);
        return null;
    }


    /**
     * 计算两者最近争用区
     * vehicleId 为计算主体
     */
    private DisputeArea nearestDisputeArea(Integer vehicleId, Integer otherId) {
        GlobalPath globalPath = BaseCacheUtil.getGlobalPath(vehicleId);
        if (null != globalPath) {
            WorkPathInfo workPathInfo = globalPath.getWorkPathInfo();
            int nearestId = workPathInfo.getNearestId();
            DisputeArea nearestDisputeArea = null;
            int id = 1000000;//最近争用区距离
            Iterator<DisputeArea> iterator = DISPUTE_AREAS.iterator();
            while (iterator.hasNext()) {
                DisputeArea disputeArea = iterator.next();
                if (disputeArea.getUseObjects().contains(vehicleId) &&
                        disputeArea.getUseObjects().contains(otherId)) {
                    Integer index = disputeArea.getIndexMap().get(vehicleId);
                    if (null != index && index < id) {
                        id = index - nearestId;
                        nearestDisputeArea = disputeArea;
                    }
                }
            }

            log.debug("【争用区】:非主体车{}的最近争用区为{},indexs={},lockId={}", vehicleId, nearestDisputeArea.getId(), nearestDisputeArea.getIndexMap(), nearestDisputeArea.getUniqueId());
            return nearestDisputeArea;
        }
        return null;
    }

    /**
     * 争用区锁定
     */
    private void setLock(Integer lockId, Integer id1, Integer id2) {
        Iterator<DisputeArea> iterator = DISPUTE_AREAS.iterator();
        while (iterator.hasNext()) {
            DisputeArea disputeArea = iterator.next();
            if (disputeArea.getUseObjects().contains(id1) && disputeArea.getUseObjects().contains(id2)) {
                log.debug("【争用区】:争用区锁定:id={},lockId={},indexs={}", disputeArea.getId(), lockId, disputeArea.getIndexMap());
                disputeArea.setOccupyObj(lockId);
            }
        }
    }


    /**
     * 清理过期争用区
     */
    private void clearExpirationDisputeArea() {
        Iterator<DisputeArea> iterator = DISPUTE_AREAS.iterator();
        List<DisputeArea> objects = new ArrayList<>();
        long curTime = BaseUtil.getCurTime();
        while (iterator.hasNext()) {
            DisputeArea next = iterator.next();
            if (curTime - next.getAddTime() > 2000) {
                objects.add(next);
            }
        }
        if (objects.size() > 0) {
            DISPUTE_AREAS.removeAll(objects);
        }
    }

    /**
     * 计算争用区
     */
    private void calculateDisputeArea(GlobalPath globalPath, Integer vehicleId, Set<Location> locations) {
        Set<VehicleTask> vehicleTasks = getLocationByClass(locations, VehicleTask.class);
        int nearestId = globalPath.getWorkPathInfo().getNearestId();
        int sectionPathEndId = globalPath.getWorkPathInfo().getSectionPathEndId();
        List<Vertex> vertexs = globalPath.getVertexs();
        if (nearestId >= sectionPathEndId || !BaseUtil.CollectionNotNull(vehicleTasks)) {
            return;
        }
        VehicleTask curTask = BaseCacheUtil.getVehicleTask(vehicleId);
        for (VehicleTask vehicleTask : vehicleTasks) {
            Integer otherVehicleId = vehicleTask.getVehicleId();
            if (!otherVehicleId.equals(vehicleId)) {
                GlobalPath otherGlobalPath = BaseCacheUtil.getGlobalPath(otherVehicleId);
                WorkPathInfo otherWorkPathInfo = BaseCacheUtil.getWorkPathInfo(otherVehicleId);
                if (BaseUtil.allObjNotNull(otherGlobalPath, otherWorkPathInfo) && BaseUtil.CollectionNotNull(otherGlobalPath.getVertexs())) {
                    List<Vertex> otherVertexs = otherGlobalPath.getVertexs();
                    int otherNearestId = otherWorkPathInfo.getNearestId();
                    int pointNum = otherWorkPathInfo.getPathPointNum();

                    //计算最近争用区
                    Rectangle curRec = new Rectangle(0, 0, curTask.stopSafePADistance() * 2, curTask.stopSafeRLDistance() * 2, 0);
                    Rectangle otherRec = new Rectangle(0, 0, vehicleTask.stopSafePADistance() * 2, vehicleTask.stopSafeRLDistance() * 2, 0);
                    out:
                    for (int i = nearestId; i < sectionPathEndId; i += 3) {
                        for (int j = otherNearestId; j < pointNum; j += 3) {
                            double x = vertexs.get(i).getX();
                            double y = vertexs.get(i).getY();

                            curRec.setCenterX(vertexs.get(i).getX());
                            curRec.setCenterY(vertexs.get(i).getY());
                            curRec.setAngle(vertexs.get(i).getDirection());

                            otherRec.setCenterX(otherVertexs.get(j).getX());
                            otherRec.setCenterY(otherVertexs.get(j).getY());
                            otherRec.setAngle(otherVertexs.get(j).getDirection());

                            //碰撞检测
                            if (CollisionDetectUtil.collisionDetectByAngle(curRec, otherRec)) {
                                Map<Integer, Integer> indexMap = new HashMap<>();
                                indexMap.put(vehicleId, i);
                                indexMap.put(otherVehicleId, j);
                                addDisputeArea(vehicleId, indexMap, x, y);
                                log.warn("【{}】计算最近争用区，进入碰撞检测,index={}", vehicleId, indexMap);
                                break out;
                            }

                            double s = DispatchUtil.GetDistance(otherGlobalPath, otherNearestId, j);
                            if (s > CalculateConfig.DISPUTE_RANGE) {//只计算预警范围内的争用区,减少计算
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 添加争用区对象
     */
    private DisputeArea addDisputeArea(int vehicleId, Map<Integer, Integer> indexMap, double x, double y) {
        GlobalPath path = BaseCacheUtil.getGlobalPath(vehicleId);
        if (null == path) {
            log.error("【{}】添加争用区对象异常", vehicleId);
            return null;
        }
        DisputeEntry disputeEntry = new DisputeEntry(path.getId(), x, y, vehicleId);
        MongoStore.addToMongo(MongoKeyPool.VEHICLE_DISPUTE_POINTS, disputeEntry);
        DisputeArea disputeArea = createDisputeArea(indexMap, x, y);
        log.debug("【争用区】:新增争用区:{}", disputeArea.getIndexMap());
        DISPUTE_AREAS.add(disputeArea);
        return disputeArea;
    }

    /**
     * 创建争用区对象
     */
    private DisputeArea createDisputeArea(Map<Integer, Integer> indexMap, double x, double y) {
        DisputeArea disputeArea = new DisputeArea();
        disputeArea.setId(DISPUTE_AREA_ID.incrementAndGet());
        disputeArea.setAddTime(BaseUtil.getCurTime());
        for (Map.Entry<Integer, Integer> entry : indexMap.entrySet()) {
            disputeArea.addDisputeObj(entry.getKey());
        }
        disputeArea.setIndexMap(indexMap);
        disputeArea.setX(x);
        disputeArea.setY(y);
        return disputeArea;
    }

    @Override
    public CalculateTypeEnum getCalculateType() {
        return CalculateTypeEnum.VEHICLE_DISPUTEAREA_LIMITING;
    }
}
