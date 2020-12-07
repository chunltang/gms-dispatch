package com.baseboot.enums;

import com.baseboot.entry.global.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 驾驶模式类型
 */
public enum DriveTypeEnum implements IEnum {

    MANUAL_DRIVE("0", "驾驶员驾驶"),

    MANNED_DRIVE("1", "人工接管"),

    AUTOMATIC_DRIVE("2", "自动驾驶");

    private String value;

    private String desc;

    DriveTypeEnum(String value, String desc) {
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

    public static DriveTypeEnum getEnum(String value) {
        for (DriveTypeEnum modeState : DriveTypeEnum.values()) {
            if (modeState.getValue().equals(value)) {
                return modeState;
            }
        }
        return null;
    }
}
