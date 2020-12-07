package com.baseboot.service.dispatch.manager;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

/**
 * 路径生成异常状态
 */
@Slf4j
public enum PathErrorEnum {

    NONE("6", "没有规划任务"),
    ERROR("-1", "计算异常"),
    SUCCESS("0", "规划成功"),
    TIMEOUT("-301", "规划超时"),
    NO_USERD_PATH("-302", "无可行路径"),
    START_CRASH_CHECK_FAIL("-303", "起点碰撞不通过"),
    END_CRASH_CHECK_FAIL("-304", "终点碰撞不通过"),
    MAP_UNUSED("-305", "地图不可行");

    private String value;

    private String desc;

    PathErrorEnum(String value, String desc) {
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

    public static PathErrorEnum getPathErrorCode(String code){
        for (PathErrorEnum anEnum : PathErrorEnum.values()) {
            if(anEnum.getValue().equals(code)){
                return anEnum;
            }
        }
        log.error("没有对应路径异常枚举类型！");
        return null;
    }
}
