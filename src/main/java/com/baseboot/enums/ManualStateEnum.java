package com.baseboot.enums;

import com.baseboot.entry.global.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 人工接管方式
 */
public enum ManualStateEnum implements IEnum {

    NONE_MODE("0", "无接管"),
    STEERING_WHEEL_MODE("1", "方向盘接管"),
    AUTOMATIC_PEDESTAL_MODE("2", "制动踏板接管");

    private String value;

    private String desc;

    ManualStateEnum(String value, String desc) {
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

    public static ManualStateEnum getEnum(String value) {
        for (ManualStateEnum modeState : ManualStateEnum.values()) {
            if (modeState.getValue().equals(value)) {
                return modeState;
            }
        }
        return null;
    }
}
