package com.baseboot.entry.global;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LogType implements IEnum {

    INFO("1", "提示信息"),
    WARN("2", "警告信息"),
    ERROR("3", "异常信息"),
    INTERFACE_REQUEST("4", "接口请求"),
    INTERFACE_RESPONSE("5", "接口响应"),
    COMMAND("6", "地面下发指令"),
    VEHICLE_TASK_TYPE("7", "车载任务类型"),
    LOAD_COMMAND_REQUEST("8", "挖掘机终端指令请求");

    private String value;

    private String desc;

    LogType(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @JsonValue
    public String getValue() {
        return this.value;
    }

    @Override
    public String getDesc() {
        return this.desc;
    }
}
