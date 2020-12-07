package com.baseboot.service.dispatch.input;

import com.baseboot.entry.global.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 交互式状态
 */
public enum InteractiveStateEnum implements IEnum<String> {

    FREE("6", "车辆空闲"),
    PATH_CREATING("7", "正在生成路径"),
    PATH_CREATED_SUCESS("8", "交互式生成路径完成"),
    PATH_RUNING("9", "交互式路径运行"),
    PATH_CREATED_ERROR("10", "交互式路径生成失败"),
    PATH_INTERRUPT("11", "交互式路径中断");

    private String value;

    private String desc;

    InteractiveStateEnum(String value, String desc) {
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

    public static InteractiveStateEnum getEnum(String value) {
        for (InteractiveStateEnum anEnum : InteractiveStateEnum.values()) {
            if (anEnum.getValue().equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 判断是否是交互式任务状态
     */
    public static boolean isInteractiveState(String state) {
        InteractiveStateEnum anEnum = getEnum(state);
        if (null != anEnum && !anEnum.equals(InteractiveStateEnum.FREE)) {
            return true;
        }
        return false;
    }
}
