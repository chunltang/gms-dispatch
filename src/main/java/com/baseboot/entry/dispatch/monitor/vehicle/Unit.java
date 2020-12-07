package com.baseboot.entry.dispatch.monitor.vehicle;

import com.baseboot.common.annotation.NotNull;
import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.Assert;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.area.*;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.BaseRedisCache;
import com.baseboot.entry.global.RedisKeyPool;
import com.baseboot.entry.map.Point;
import com.baseboot.enums.DispatchStateEnum;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.input.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Data
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class Unit implements BaseRedisCache {

    @NotNull
    private Integer unitId;

    private Set<VehicleTask> vehicleTasks = new HashSet<>();

    @NotNull
    private LoadArea loadArea;

    @NotNull
    private UnloadArea unloadArea;

    private Integer cycleTimes = -1;

    private Integer workTimes = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GTM+8")
    private Date endTime;

    private boolean needDelSign = false;//是否需要删除

    private UnitStateEnum unitState = UnitStateEnum.RUNNING;//默认运行状态

    private Set<VehicleTask> nextStopVehicles = Collections.synchronizedSet(new HashSet<>());//下个循环需要停止的车辆


    /**
     * 调度单元新增车辆,true为新增成功
     */
    public boolean addVehicleTask(VehicleTask vehicleTask) {
        Assert.notNull(vehicleTask, "[vehicleTask]不能为空");
        Integer vehicleId = vehicleTask.getVehicleId();
        for (VehicleTask task : vehicleTasks) {
            if (task.getVehicleId().equals(vehicleId)) {
                log.debug("该车辆已在调度单元中,unitId={},vehicleId={}", task.getHelper().getUnitId(), vehicleId);
                return false;
            }
        }
        log.debug("调度单元新增车辆,unitId={},vehicleId={}", unitId, vehicleId);
        vehicleTask.getHelper().setUnitId(unitId);
        vehicleTasks.add(vehicleTask);
        return true;
    }

    /**
     * 调度单元移除车辆
     */
    public boolean removeVehicleTask(Integer vehicleId) {
        synchronized (BaseCacheUtil.objectLock("removeVehicleTask-" + unitId)) {
            Iterator<VehicleTask> iterator = vehicleTasks.iterator();
            while (iterator.hasNext()) {
                VehicleTask task = iterator.next();
                if (task.getVehicleId().equals(vehicleId)) {
                    log.debug("调度单元移除车辆,unitId={},vehicleId={}", unitId, vehicleId);
                    VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
                    vehicleTask.getHelper().setUnitId(null);
                    iterator.remove();
                    vehicleTask.getHelper().getVehicleStateManager().stopTask();//改为空闲
                    return true;
                }
            }
            log.debug("该车辆[{}]不在调度单元中!", vehicleId);
            return false;
        }
    }

    /**
     * 判断是否调度单元是否有车,true为有车
     */
    public boolean hasUnitVehicles() {
        return !vehicleTasks.isEmpty();
    }

    /**
     * 车子完成一次任务通知,true为停止运行调度单元任务
     */
    public boolean vehicleFinishOneTask(Integer vehicleId) {
        if (cycleTimes > 0) {
            cycleTimes--;
        }
        //计算调度调度单元任务是否已完成
        if (isFinished()) {
            for (VehicleTask vehicleTask : vehicleTasks) {
                if (vehicleId.equals(vehicleTask.getVehicleId())) {
                    log.debug("【调度单元任务完成】，车辆[{}]停止运行", vehicleId);
                    //停止运行
                    stopVehicle(vehicleTask);
                    unitState = UnitStateEnum.CLOSE;
                    break;
                }
            }
            return true;
        }
        //需要删除调度单元
        if (needDelSign) {
            for (VehicleTask vehicleTask : vehicleTasks) {
                if (vehicleId.equals(vehicleTask.getVehicleId())) {
                    //停止运行
                    stopVehicle(vehicleTask);
                    log.debug("调度单元[{}]删除通知,移除完成卸载的车辆[{}]", unitId, vehicleTask.getVehicleId());
                    removeVehicleTask(vehicleTask.getVehicleId());
                    break;
                }
            }
            delUnit();
            return true;
        }

        //停止需要停止的车辆
        if (nextStopVehicles.size() > 0) {
            Iterator<VehicleTask> iterator = nextStopVehicles.iterator();
            while (iterator.hasNext()) {
                VehicleTask vehicleTask = iterator.next();
                stopVehicle(vehicleTask);
                iterator.remove();
            }
        }
        updateCache();
        return false;
    }

    /**
     * 判断调度任务是否已完成
     */
    public boolean isFinished() {
        return cycleTimes == 0 || (cycleTimes > 0 && cycleTimes - getGoLoadNums() - getGoUnloadNums() <= 0) ||
                (null != endTime && endTime.getTime() < BaseUtil.getCurTime());
    }

    /**
     * 删除调度单元通知
     */
    public boolean delUnitNotify() {
        needDelSign = true;
        for (VehicleTask vehicleTask : vehicleTasks) {
            DispatchStateEnum dispatchState = vehicleTask.getHelper().getDispatchStateManager().getDispatchState();
            if (DispatchStateEnum.isNoLoadState(dispatchState)) {
                //如果是空载状态，直接停止运行
                stopVehicle(vehicleTask);
                //移除调度单元车辆
                log.debug("调度单元[{}]删除通知,移除空载车辆[{}]", unitId, vehicleTask.getVehicleId());
                removeVehicleTask(vehicleTask.getVehicleId());
            }
        }
        return delUnit();
    }

    /**
     * 添加下个循环需要停止的车辆
     */
    public void addNextSopVehicle(Integer vehicleId) {
        for (VehicleTask vehicleTask : vehicleTasks) {
            if (vehicleTask.getVehicleId().equals(vehicleId)) {
                nextStopVehicles.add(vehicleTask);
                return;
            }
        }
    }

    /**
     * 获取去装载任务的个数
     */
    private int getGoLoadNums() {
        int nums = 0;
        for (VehicleTask vehicleTask : vehicleTasks) {
            DispatchInput input = InputCache.getDispatchInput(vehicleTask.getVehicleId());
            if (input instanceof DispatchUnitInput) {//是在运行调度单元任务
                String taskState = vehicleTask.getHelper().getTaskStateManager().getTaskState();
                DispatchUnitStateEnum anEnum = DispatchUnitStateEnum.getEnum(taskState);
                if (DispatchUnitStateEnum.isNoLoadState(anEnum)) {
                    nums++;
                }
            }
        }
        return nums;
    }

    /**
     * 获取去卸载任务的车辆个数
     */
    private int getGoUnloadNums() {
        int nums = 0;
        for (VehicleTask vehicleTask : vehicleTasks) {
            DispatchInput input = InputCache.getDispatchInput(vehicleTask.getVehicleId());
            if (input instanceof DispatchUnitInput) {//是在运行调度单元任务
                String taskState = vehicleTask.getHelper().getTaskStateManager().getTaskState();
                DispatchUnitStateEnum anEnum = DispatchUnitStateEnum.getEnum(taskState);
                if (DispatchUnitStateEnum.isLoadState(anEnum)) {
                    nums++;
                }
            }
        }
        return nums;
    }


    /**
     * 停止车辆
     */
    private void stopVehicle(VehicleTask vehicleTask) {
        if (null != vehicleTask) {
            log.debug("【{}】调度单元[{}]车辆停止", vehicleTask.getVehicleId(), unitId);
            vehicleTask.changeStartFlag(false);
            vehicleTask.stopVehicle();
            vehicleTask.getHelper().getVehicleStateManager().stopTask();
        }
    }

    private boolean delUnit() {
        //如果调度单元没车，则删除调度单元
        if (vehicleTasks.isEmpty()) {
            unitState = UnitStateEnum.DELETE;
            return BaseCacheUtil.removeUnit(unitId);
        }
        unitState = UnitStateEnum.CLOSING;
        return false;
    }

    /*********************************特殊点位置获取**********************************/

    /**
     * 获取装载区排队点
     */
    public Point getLoadQueuePoint() {
        if (null != loadArea) {
            QueuePoint queuePoint = loadArea.getQueuePoint();
            if (null != queuePoint) {
                return queuePoint.getQueuePoint();
            }
        }
        return null;
    }

    /**
     * 获取装载排队点id
     */
    public Integer getLoadQueueId() {
        if (null != loadArea) {
            QueuePoint queuePoint = loadArea.getQueuePoint();
            if (null != queuePoint) {
                return BaseUtil.typeTransform(queuePoint.getQueueId(), Integer.class);
            }
        }
        return null;
    }

    /**
     * 获取装载点
     */
    public Point getLoadPoint() {
        if (null != loadArea) {
            LoadPoint loadPoint = loadArea.getLoadPoint();
            if (null != loadPoint) {
                return loadPoint.getLoadPoint();
            }
        }
        log.error("！！！！！获取装载点失败！！！！！");
        return null;
    }

    /**
     * 获取装载点id
     */
    public Integer getLoadPointId() {
        if (null != loadArea) {
            LoadPoint loadPoint = loadArea.getLoadPoint();
            if (null != loadPoint) {
                return BaseUtil.typeTransform(loadPoint.getLoadId(), Integer.class);
            }
        }
        return null;
    }

    /**
     * 获取卸载区排队点
     */
    public Point getUnloadQueuePoint() {
        if (null != unloadArea) {
            QueuePoint queuePoint = unloadArea.getQueuePoint();
            if (null != queuePoint) {
                return queuePoint.getQueuePoint();
            }
        }
        return null;
    }

    /**
     * 获取卸载排队点id
     */
    public Integer getUnloadQueueId() {
        if (null != unloadArea) {
            QueuePoint queuePoint = unloadArea.getQueuePoint();
            if (null != queuePoint) {
                return BaseUtil.typeTransform(queuePoint.getQueueId(), Integer.class);
            }
        }
        return null;
    }

    /**
     * 获取卸载点
     */
    public Point getUnloadPoint() {
        if (null != unloadArea) {
            UnloadPoint[] unloadPoints = unloadArea.getUnloadPoints();
            if (BaseUtil.arrayNotNull(unloadPoints)) {
                return unloadPoints[0].getUnloadPoint();
            }
        }
        return null;
    }

    /**
     * 获取卸载点id
     */
    public Integer getUnloadId() {
        if (null != unloadArea) {
            UnloadPoint[] unloadPoints = unloadArea.getUnloadPoints();
            if (BaseUtil.arrayNotNull(unloadPoints)) {
                return BaseUtil.typeTransform(unloadPoints[0].getUnloadId(), Integer.class);
            }
        }
        return null;
    }

    /**
     * 更新缓存
     */
    @Override
    public void updateCache() {
        Map<String, Object> params = new HashMap<>();
        params.put("unitId", unitId);
        params.put("state", unitState);
        params.put("workTimes", workTimes);
        params.put("cycleTimes", cycleTimes);
        params.put("endTime", endTime);
        RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.DISPATCH_UNIT + unitId, BaseUtil.toJson(params));
    }

    public enum UnitStateEnum {

        DELETE("0", "删除"),
        RUNNING("1", "开启"),
        CLOSE("3", "关闭"),
        CLOSING("4", "关闭中");

        private String value;

        private String desc;

        UnitStateEnum(String value, String desc) {
            this.value = value;
            this.desc = desc;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }

        public String getDesc() {
            return this.desc;
        }
    }


    @Override
    public String toString() {
        return BaseUtil.format("unitId={}，loadArea={}，unloadArea={}，cycTimes={}，workTimes={}，endTime={}",
                unitId, loadArea, unloadArea, cycleTimes, workTimes, endTime);
    }
}
