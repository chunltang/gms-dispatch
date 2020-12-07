package com.baseboot.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 任务区状态：装载区
 */
public enum LoadAreaStateEnum {

    OFFLINE("1", "离线/停工->任务点开工"),
    DELAY("2", "延迟，不能进车->可以点进车，可以停工"),
    READY("3", "任务点就绪，可以进车->只能取消进车"),
    RELEVANCE("4", "关联/装载区已有车装载，不能进车->可以取消进车"),
    PREPARE("5", "准备->开装"),
    WORKING("6", "作业/装载->完成装"),
    ERROR("7", "异常");

    private String value;

    private String desc;


    LoadAreaStateEnum(String value, String desc) {
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

    public static LoadAreaStateEnum getAreaState(String value) {
        for (LoadAreaStateEnum anEnum : LoadAreaStateEnum.values()) {
            if (anEnum.getValue().equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}

