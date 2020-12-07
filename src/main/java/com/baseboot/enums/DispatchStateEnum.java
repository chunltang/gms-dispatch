package com.baseboot.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 车辆调度状态
 */
public enum DispatchStateEnum {

    NOLOAD_FREE("1", "空载空闲"),
    NOLOAD_RUN("2", "空载行驶"),
    LOAD_FREE("3", "满载空闲"),
    LOAD_RUN("4", "满载行驶"),
    LOAD_WORKING("5", "装载工作状态"),
    NOLOAD_PARK("6", "空载泊车"),
    UNLOAD_WORKING("5", "卸载工作状态");
    /**
     1 空载空闲
     2 空载行驶
     3 满载空闲
     4 满载行驶
     5 工作状态
     6 空载泊车
     * */

    private String value;

    private String desc;

    DispatchStateEnum(String value, String desc) {
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

    /**
     * 空载
     */
    public static boolean isNoLoadState(DispatchStateEnum state) {
        if (DispatchStateEnum.NOLOAD_FREE.equals(state) ||
                DispatchStateEnum.NOLOAD_RUN.equals(state) ||
                DispatchStateEnum.NOLOAD_PARK.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * 重载
     */
    public static boolean isLoadState(DispatchStateEnum state) {
        if (DispatchStateEnum.LOAD_FREE.equals(state) ||
                DispatchStateEnum.LOAD_RUN.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * 工作状态
     * */
    public static boolean isWorkingState(DispatchStateEnum state) {
        if (DispatchStateEnum.LOAD_WORKING.equals(state) ||
                DispatchStateEnum.UNLOAD_WORKING.equals(state)) {
            return true;
        }
        return false;
    }
}
