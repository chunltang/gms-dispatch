package com.baseboot.enums;

import com.baseboot.entry.global.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 是否枚举
 */
public enum WhetherEnum implements IEnum {

    NO("0", "否"),
    YES("1", "是");


    private String value;

    private String desc;

    WhetherEnum(String value, String desc) {
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

    public static WhetherEnum getEnum(String value) {
        for (WhetherEnum modeState : WhetherEnum.values()) {
            if (modeState.getValue().equals(value)) {
                return modeState;
            }
        }
        return null;
    }
}
