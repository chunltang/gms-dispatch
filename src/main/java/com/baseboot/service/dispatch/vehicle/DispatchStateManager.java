package com.baseboot.service.dispatch.vehicle;

import com.baseboot.enums.DispatchStateEnum;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DispatchStateManager {

    private  Integer vehicleId;

    private VehicleTaskHelper helper;

    public DispatchStateManager(VehicleTaskHelper helper) {
        this.helper = helper;
        this.vehicleId = helper.getVehicleId();
    }

    /**
     * 修改调度状态
     */
    public void changeDispatchState(DispatchStateEnum dispState) {
        log.debug("修改调度状态:{}={}", vehicleId, dispState.getDesc());
        helper.getLiveInfo().setDispState(dispState);
    }

    /**
     * 获取调度状态
     */
    public DispatchStateEnum getDispatchState() {
        return helper.getLiveInfo().getDispState();
    }

    /**
     * 是否重载
     */
    public boolean isDispatchLoadState() {
        return DispatchStateEnum.isLoadState(getDispatchState());
    }

    /**
     * 空载
     */
    public boolean isDispatchNoLoadState() {
        return DispatchStateEnum.isNoLoadState(getDispatchState());
    }
}
