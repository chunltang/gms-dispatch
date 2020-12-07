package com.baseboot.service.dispatch.vehicle;

import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.input.DefaultInput;
import com.baseboot.service.dispatch.input.DispatchInput;

public class InputStateManager {

    private  Integer vehicleId;

    private VehicleTaskHelper helper;

    public InputStateManager(VehicleTaskHelper helper) {
        this.helper = helper;
        this.vehicleId = helper.getVehicleId();
    }

    /**
     * 调度输入是否是默认输入,true为是
     */
    public boolean isDefaultInput() {
        return DefaultInput.class.equals(helper.getPathManager().getInput().getClass());
    }

    /**
     * 判断当前调度输入
     */
    public boolean isGetInputType(Class<? extends DispatchInput> inputClass) {
        return null != inputClass && inputClass.equals(helper.getPathManager().getInput().getClass());
    }

    /**
     * 获取当前调度输入
     */
    public DispatchInput getCurInputType() {
        return helper.getPathManager().getInput();
    }
}
