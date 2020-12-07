package com.baseboot.service.dispatch.helpers;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.entry.dispatch.monitor.vehicle.Unit;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleLiveInfo;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.map.Point;
import com.baseboot.entry.map.SemiStatic;
import com.baseboot.enums.AreaTypeEnum;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.input.DefaultInput;
import com.baseboot.service.dispatch.vehicle.*;
import com.baseboot.service.dispatch.manager.PathManager;
import com.baseboot.service.dispatch.vehicle.commandHandle.TaskCodeCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * 车辆任务辅助工具
 */
@Slf4j
public class VehicleTaskHelper implements Helper<VehicleTask> {

    private VehicleTask vehicleTask;

    private Integer vehicleId;

    private PathManager pathManager;

    private VehicleMonitorManager vehicleMonitorManager;

    private VehicleStateManager vehicleStateManager;

    private Integer unitId;

    private boolean collectionVehicle = false;//是否是采集车

    public VehicleTaskHelper() {

    }

    @Override
    public VehicleTask getHelpClazz() {
        return vehicleTask;
    }

    @Override
    public void initHelpClazz(VehicleTask vehicleTask) {
        this.vehicleTask = vehicleTask;
        this.vehicleId = vehicleTask.getVehicleId();
        this.initDispatchInputManager();
        this.vehicleStateManager = new VehicleStateManager(this);
        this.vehicleMonitorManager = new VehicleMonitorManager(this);
    }

    private void initDispatchInputManager() {
        this.pathManager = new PathManager(this);
        this.pathManager.initInput(new DefaultInput());
    }

    public void setCollectionVehicle(boolean isCollectionVehicle) {
        log.debug("【{}】设置为采集车,flag={}", vehicleId, isCollectionVehicle);
        this.collectionVehicle = isCollectionVehicle;
    }

    /**
     * 检查车辆是否断开连接,false断开连接
     */
    public boolean checkLink() {
        long receiveTime = getLiveInfo().getReceiveTime();
        return BaseUtil.getCurTime() - receiveTime <= BaseConstant.VEHICLE_EXPIRATION_TIME;
    }


    /*****************************监听数据更新******************************/

    public VehicleLiveInfo getLiveInfo() {
        return this.vehicleMonitorManager.getVehicleLiveInfo();
    }

    public Monitor getMonitor() {
        return getLiveInfo().getMonitor();
    }

    /**
     * 地图加载更新,重置参数
     */
    public void mapLoadClear() {
        log.debug("加载新地图，【{}】重置参数", getVehicleId());
        Unit unit = getSelfUnit();
        if (null != unit) {
            unit.removeVehicleTask(getVehicleId());//移除调度单元
        }
        setUnitId(null);//重置调度单元
        clearWebParams();
        VehicleLiveInfo liveInfo = getLiveInfo();
        liveInfo.setTaskSpotId(0);
        liveInfo.setTaskAreaId(0);
        liveInfo.setObsFlag(false);
    }

    /**
     * 切换工作状态，重新赋值,不然调度车辆位置出现跳变
     */
    public void clearWebParams() {
        VehicleLiveInfo liveInfo = getLiveInfo();
        liveInfo.setNowPathId(0);
        liveInfo.setNowDistance(0D);
        liveInfo.setEndDistance(0D);
    }


    /**
     * 设置任务点
     */
    public void setTaskSpot(Integer taskSpotId) {
        if (null != taskSpotId) {
            VehicleLiveInfo liveInfo = getLiveInfo();
            liveInfo.setTaskSpotId(taskSpotId);
        }
    }

    /********************************设置、获取车辆数状态属性*****************************/


    /**
     * 获取车辆所在调度单元
     */
    public Unit getSelfUnit() {
        if (null != unitId) {
            return BaseCacheUtil.getUnit(unitId);
        }
        log.debug("该车辆没有分配调度单元,{}", vehicleId);
        return null;
    }

    /**
     * 获取当前所在区域
     */
    public SemiStatic getCurArea() {
        return getLiveInfo().getSemiStatic();
    }

    /**
     * 是否在装载区内
     */
    public boolean isInnerLoadArea() {
        return AreaTypeEnum.LOAD_AREA.equals(getLiveInfo().getSemiStatic().getAreaType());
    }

    /**
     * 是否在卸载区内
     */
    public boolean isInnerUnloadArea() {
        return AreaTypeEnum.UNLOAD_MINERAL_AREA.equals(getLiveInfo().getSemiStatic().getAreaType()) ||
                AreaTypeEnum.UNLOAD_WASTE_AREA.equals(getLiveInfo().getSemiStatic().getAreaType());
    }

    /**
     * 是否在装卸区内
     */
    public boolean isInnerWorkingArea() {
        return isInnerLoadArea() || isInnerUnloadArea();
    }

    /**
     * 获取当前速度
     */
    public double getCurSpeed() {
        Monitor monitor = getLiveInfo().getMonitor();
        if (null != monitor) {
            return monitor.getCurSpeed();
        }
        return 0;
    }

    public Point getCurLocation() {
        Monitor monitor = getLiveInfo().getMonitor();
        if (null != monitor) {
            Point point = new Point();
            point.setX(monitor.getXworld());
            point.setY(monitor.getYworld());
            point.setZ(monitor.getZworld());
            point.setYawAngle(monitor.getYawAngle());
            return point;
        }
        return null;
    }


    /*************************** mapGet and asyncSet *****************************/

    public VehicleTask getVehicleTask() {
        return vehicleTask;
    }

    public Integer getVehicleId() {
        return vehicleId;
    }

    public PathManager getPathManager() {
        return pathManager;
    }


    @Override
    public String toString() {
        return "" + this.vehicleId;
    }

    public TaskCodeCommand getTaskCodeCommand() {
        return vehicleMonitorManager.getAttrCommand(TaskCodeCommand.class);
    }

    public VehicleMonitorManager getVehicleMonitorManager() {
        return vehicleMonitorManager;
    }

    public Integer getUnitId() {
        return unitId;
    }

    public void setUnitId(Integer unitId) {
        this.unitId = unitId;
    }

    public VehicleStateManager getVehicleStateManager() {
        return vehicleStateManager;
    }

    public DispatchStateManager getDispatchStateManager() {
        return this.vehicleStateManager.getDispatchStateManager();
    }


    public InputStateManager getInputStateManager() {
        return this.vehicleStateManager.getInputStateManager();
    }

    public RunStateManager getRunStateManager() {
        return this.vehicleStateManager.getRunStateManager();
    }

    public TaskStateManager getTaskStateManager() {
        return this.vehicleStateManager.getTaskStateManager();
    }

    public CommandStateManager getCommandStateManager() {
        return this.vehicleStateManager.getCommandStateManager();
    }

    public boolean isCollectionVehicle() {
        return collectionVehicle;
    }
}
