package com.baseboot.enums;

import com.baseboot.entry.global.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 驾驶模式类型
 */
public enum DriveType2Enum implements IEnum {

    MANUAL_DRIVE("0", "驾驶员驾驶"),

    AUTOMATIC_DRIVE("1", "自动驾驶");

    private String value;

    private String desc;

    DriveType2Enum(String value, String desc) {
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

    public static DriveType2Enum getEnum(String value) {
        for (DriveType2Enum modeState : DriveType2Enum.values()) {
            if (modeState.getValue().equals(value)) {
                return modeState;
            }
        }
        return null;
    }
}
