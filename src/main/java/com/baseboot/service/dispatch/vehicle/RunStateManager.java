package com.baseboot.service.dispatch.vehicle;

import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.enums.ModeStateEnum;
import com.baseboot.enums.VehicleCommandEnum;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;

public class RunStateManager {

    private final Integer vehicleId;

    private VehicleTaskHelper helper;

    public RunStateManager(VehicleTaskHelper helper) {
        this.helper = helper;
        this.vehicleId = helper.getVehicleId();
    }


    /**
     * 判断车是否运行
     */
    public boolean isStart() {
        return helper.getHelpClazz().isStart();
    }

    /**
     * 判断车是否在发送轨迹
     */
    public boolean isRunning() {
        return VehicleCommandEnum.VEHAUTOTRAILFOLLOWING.equals(helper.getCommandStateManager().getPreCode());
    }

    /**
     * 获取当前车距路径终点的距离
     */
    public Double getEndPointDistance() {
        WorkPathInfo workPathInfo = BaseCacheUtil.getWorkPathInfo(vehicleId);
        if (null == workPathInfo) {
            return null;
        }
        if (workPathInfo.getPath().getVertexNum() == 0) {
            return null;
        }
        return DispatchUtil.GetDistance(workPathInfo.getPath(), workPathInfo.getNearestId(), workPathInfo.getPathPointNum() - 1);
    }


    /**
     * 重置车辆运行状态
     */
    public void resetRunState() {
        helper.getVehicleStateManager().stopTask();
        helper.getVehicleMonitorManager().changeModeStateEnum(ModeStateEnum.OFFLINE);
    }

}
