package com.baseboot.enums;

import com.baseboot.entry.global.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 连接状态
 * */
public enum ConnectionStateEnum implements IEnum {

    OFFLINE("0", "断开连接"),

    CONN("1", "连接");

    private String value;

    private String desc;

    ConnectionStateEnum(String value, String desc) {
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

    public static ConnectionStateEnum getEnum(String value) {
        for (ConnectionStateEnum modeState : ConnectionStateEnum.values()) {
            if (modeState.getValue().equals(value)) {
                return modeState;
            }
        }
        return null;
    }
}
