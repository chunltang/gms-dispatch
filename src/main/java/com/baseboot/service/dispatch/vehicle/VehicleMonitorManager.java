package com.baseboot.service.dispatch.vehicle;

import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.ClassUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.DeviceTrouble;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleLiveInfo;
import com.baseboot.entry.dispatch.path.GlobalPath;
import com.baseboot.entry.dispatch.path.Vertex;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.LogType;
import com.baseboot.entry.global.RedisKeyPool;
import com.baseboot.entry.map.Point;
import com.baseboot.entry.map.SemiStatic;
import com.baseboot.enums.*;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.calculate.CalculateConfig;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.input.DispatchInput;
import com.baseboot.service.dispatch.input.InputCache;
import com.baseboot.service.dispatch.manager.PathErrorEnum;
import com.baseboot.service.dispatch.manager.PathStateEnum;
import com.baseboot.service.dispatch.vehicle.commandHandle.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * 车辆上报数据管理
 */
@Data
@Slf4j
public class VehicleMonitorManager {

    private Integer vehicleId;

    private final VehicleTaskHelper helper;

    private VehicleLiveInfo vehicleLiveInfo;

    private Map<Class<? extends MonitorAttrHandle>, ? super MonitorAttrHandle> attrHandleHashMap = new HashMap<>();

    private Point endPoint;//任务目标终点


    public VehicleMonitorManager(VehicleTaskHelper helper) {
        this.helper = helper;
        this.vehicleId = helper.getVehicleId();
        this.vehicleLiveInfo = new VehicleLiveInfo();
        BaseCacheUtil.addLiveInfo(this.vehicleId, vehicleLiveInfo);
        newAttrCommand();
    }

    @SuppressWarnings("unchecked")
    private void newAttrCommand() {
        List<Class> allClassByAnnotation = ClassUtil.getAllClassByAnnotation(MonitorAttrCommand.class);
        for (Class aClass : allClassByAnnotation) {
            try {
                Constructor constructor = aClass.getConstructor(VehicleTaskHelper.class);
                Object instance = constructor.newInstance(helper);
                attrHandleHashMap.put(aClass, (MonitorAttrHandle) instance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 跟新实时信息
     */
    public void updateLiveInfo(Monitor monitor) {
        helper.getHelpClazz().changeRunFlag(true);
        vehicleLiveInfo.setMonitor(monitor);
        vehicleLiveInfo.setLinkFlag(true);
        vehicleLiveInfo.setUpdateTime(new Date());
        vehicleLiveInfo.setReceiveTime(BaseUtil.getCurTime());
        attrHandleHashMap.values().forEach(command -> ((MonitorAttrHandle) command).receiveCommand(monitor));
        vehicleLiveInfo.setUnitId(helper.getUnitId());
        vehicleLiveInfo.setVehicleId(vehicleId);
        calculateDeviation();
        setAreaParams();
        setControlMode();
        refreshMonitorCache();
        LogUtil.printLog(() -> {
            log.debug("【{}】 curPoint:{},curSpeed:{}", vehicleId, helper.getCurLocation(), helper.getCurSpeed());
        }, "monitor-" + vehicleId, 1000);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttrCommand(Class<T> handle) {
        return (T) attrHandleHashMap.get(handle);
    }


    /**
     * 设置模式
     */
    public void setControlMode() {
        if (!getModeState().equals(ModeStateEnum.OFFLINE)) {
            return;
        }
        changeModeStateEnum(ModeStateEnum.CONN);
    }

    /**
     * 断开连接后上传的信息
     */
    public void refreshMonitorCache() {
        this.setWebParams();
        RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.VAP_BASE_PREFIX + vehicleId, BaseUtil.toJson(vehicleLiveInfo));
        //DispatchUtil.sendVehicleInfo(vehicleId);
    }

    /**
     * 计算路径偏差
     */
    private void calculateDeviation() {
        GlobalPath globalPath = BaseCacheUtil.getGlobalPath(vehicleId);
        if (null != globalPath) {
            WorkPathInfo workPathInfo = globalPath.getWorkPathInfo();
            Point curLocation = helper.getCurLocation();
            int startId = workPathInfo.getNearestId() - 20 > 0 ? workPathInfo.getNearestId() - 20 : workPathInfo.getNearestId();
            int nearestId = DispatchUtil.getNearestId(curLocation, globalPath, startId, workPathInfo.getPathPointNum() - 1);
            if (BaseUtil.CollectionNotNull(globalPath.getVertexs())) {
                Vertex vertex = globalPath.getVertexs().get(nearestId);
                double deviation = DispatchUtil.twoPointDistance(curLocation.getX(), curLocation.getY(), vertex.getX(), vertex.getY());
                vehicleLiveInfo.setPathDeviation(deviation);
                if (helper.getRunStateManager().isRunning() && deviation > CalculateConfig.PATH_DEVIATION) {
                    LogUtil.printLog(() -> {
                        log.error("【{}】车路径偏差异常,dis={}", vehicleId, deviation);
                    }, "monitorDeviation-" + vehicleId, 1000);
                    if (deviation > CalculateConfig.PATH_DEVIATION * 2) {
                        String format = BaseUtil.format("【{}】车偏差达到[{}m]，下发安全停车!", vehicleId, CalculateConfig.PATH_DEVIATION * 2);
                        LogUtil.addLogToRedis(LogType.ERROR, "monitor-" + vehicleId, format);
                        log.error(format);
                        //tcl 释放注释
                        //CommSend.vehAutoSafeParking(vehicleId);
                    }
                }
            }
        } else {
            vehicleLiveInfo.setPathDeviation(0D);
        }
    }

    /**
     * 设置区域和任务点信息
     */
    private void setAreaParams() {
        SemiStatic aStatic = DispatchUtil.isInnerArea(helper.getCurLocation());
        if (null == aStatic) {
            if (null == this.vehicleLiveInfo.getSemiStatic()) {
                aStatic = new SemiStatic();
                aStatic.setType(AreaTypeEnum.NONE_AREA);
                this.vehicleLiveInfo.setSemiStatic(aStatic);
            }
            return;
        }

        SemiStatic old = this.vehicleLiveInfo.getSemiStatic();
        this.vehicleLiveInfo.setSemiStatic(aStatic);
        if (null != old && null != old.getId() && !old.getId().equals(aStatic.getId())) {
            log.debug("【{}】所在区域变更:newAreaId={},newAreaName={},newAreaType={},oldAreaId={},oldAreaName={},oldAreaType={}",
                    vehicleId, aStatic.getId(), aStatic.getName(), aStatic.getAreaType(),
                    old.getId(), old.getName(), old.getAreaType()
            );
        }

        DispatchInput input = InputCache.getDispatchInput(vehicleId);
        if (null != input) {
            //计算目标区域
            Point endPoint = input.getEndPoint();
            if (null != endPoint) {
                if (endPoint.equals(this.endPoint)) {
                    return;
                }
                SemiStatic innerArea = DispatchUtil.isInnerArea(endPoint);
                if (null != innerArea) {
                    this.vehicleLiveInfo.setTaskAreaId(innerArea.getId());
                    this.endPoint = endPoint;
                }
            } else {
                this.endPoint = null;
                this.vehicleLiveInfo.setTaskAreaId(0);
            }
        }
    }

    /**
     * 设置当前点和起点的距离
     */
    public void setWebParams() {
        GlobalPath globalPath = BaseCacheUtil.getGlobalPath(vehicleId);
        if (null == globalPath) {
            return;
        }
        WorkPathInfo workPathInfo = globalPath.getWorkPathInfo();
        int id1 = workPathInfo.getNearestId();
        int id2 = workPathInfo.getPathPointNum() > 0 ? workPathInfo.getPathPointNum() - 1 : 0;
        double dis1 = DispatchUtil.GetDistance(globalPath, 0, id1);
        double dis2 = DispatchUtil.GetDistance(globalPath, 0, id2);
        vehicleLiveInfo.setNowPathId(id1);
        vehicleLiveInfo.setNowDistance(dis1);
        vehicleLiveInfo.setEndDistance(dis2);
    }

    /**
     * 任务编号是否自动模式命令
     */
    public boolean isSelfMode() {
        return ModeStateEnum.SELF_MODE.equals(getCurModeState()) || isAutoDriveMode();
        //return ModeStateEnum.SELF_MODE.equals(getCurModeState());
    }

    /**
     * 判断当前是否收到自动授权申请
     */
    public boolean isReceiveApplyFor() {
        return getAttrCommand(SystemModeCommand.class).isReceiveApplyFor();
    }

    /**
     * 判断车载系统模式是否是自动驾驶模式
     */
    public boolean isAutoDriveMode() {
        Monitor monitor = this.getVehicleLiveInfo().getMonitor();
        return null != monitor &&
                (DriveTypeEnum.AUTOMATIC_DRIVE.equals(monitor.getSystemMode()) || DriveTypeEnum.MANNED_DRIVE.equals(monitor.getSystemMode()));
    }

    /**
     * 判断当前VAK指令
     */
    public boolean isTaskCode(TaskCodeEnum... targetCode) {
        return null != targetCode &&
                Arrays.asList(targetCode).contains(getTaskCode());
    }

    /**
     * 判断是否收到障碍物
     */
    public boolean isReceiveObstacle() {
        return helper.getLiveInfo().isObsFlag();
    }

    /**
     * 设置障碍物标识
     */
    public void setObstacleFlag(boolean isReceive) {
        helper.getLiveInfo().setObsFlag(isReceive);
        if (!isReceive) {//false则清理障碍物信息
            this.getAttrCommand(ObstacleCommand.class).clearObstacleInfo();
        }
    }

    /**
     * 获取当前上报任务编号
     */
    public TaskCodeEnum getTaskCode() {
        Monitor monitor = helper.getLiveInfo().getMonitor();
        if (null != monitor) {
            return TaskCodeEnum.getEnum(String.valueOf(monitor.getCurrentTaskCode()));
        }
        return null;
    }

    /**
     * 获取当前车辆模式编号
     */
    public ModeStateEnum getVakMode() {
        Monitor monitor = helper.getLiveInfo().getMonitor();
        if (null != monitor) {
            return ModeStateEnum.getEnum(String.valueOf(monitor.getVakMode()));
        }
        return null;
    }

    /**
     * 重置监控数据
     */
    public void resetMonitorInfo() {
        vehicleLiveInfo.setPathState(PathStateEnum.FREE);
        vehicleLiveInfo.setPathError(PathErrorEnum.NONE);
        vehicleLiveInfo.setPathDeviation(0D);
        vehicleLiveInfo.setTaskAreaId(null);
        //vehicleLiveInfo.setObsFlag(false);
        vehicleLiveInfo.setTaskSpotId(null);
        vehicleLiveInfo.setEndDistance(0D);
        vehicleLiveInfo.setNowDistance(0D);
        vehicleLiveInfo.setNowPathId(null);
        vehicleLiveInfo.setTaskType(TaskTypeEnum.NONE);
    }


    /**
     * 修改控制模式
     */
    public boolean changeModeStateEnum(ModeStateEnum modeState) {
        synchronized (this.helper) {
            if (getModeState().equals(modeState)) {
                return false;
            }
            log.debug("修改控制模式:{}=【{}】", vehicleId, modeState.getDesc());
            helper.getLiveInfo().setModeState(modeState);
            if (ModeStateEnum.OFFLINE.equals(modeState)) {//断开连接的操作
                this.getAttrCommand(ObstacleCommand.class).clearObstacleInfo();
                this.vehicleLiveInfo.getMonitor().setVecObstacle(null);
            }
            return true;
        }
    }

    public boolean isModeState(ModeStateEnum... targetState) {
        return null != targetState &&
                Arrays.asList(targetState).contains(getModeState());
    }

    /**
     * 获取车辆上报的控制模式
     */
    public ModeStateEnum getModeState() {
        return helper.getLiveInfo().getModeState();
    }

    /**
     * 获取当前车辆的控制模式
     */
    public ModeStateEnum getCurModeState() {
        return getAttrCommand(VakModeCommand.class).getCurControlMode();
    }
}
