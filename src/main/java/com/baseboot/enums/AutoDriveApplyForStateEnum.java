package com.baseboot.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 自动驾驶申请状态
 */
public enum AutoDriveApplyForStateEnum {

    NONE("0", "复位"),
    MANUAL_DRIVE_APPLICATION("1", "授权进入驾驶员驾驶模式"),
    AUTOMATIC_DRIVE_APPLICATION("2", "授权进入自动驾驶模式");

    private String value;

    private String desc;

    AutoDriveApplyForStateEnum(String value, String desc) {
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

    public static AutoDriveApplyForStateEnum getEnum(String value) {
        for (AutoDriveApplyForStateEnum modeState : AutoDriveApplyForStateEnum.values()) {
            if (modeState.getValue().equals(value)) {
                return modeState;
            }
        }
        return null;
    }
}
