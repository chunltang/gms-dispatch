package com.baseboot.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 任务类型
 * */
public enum TaskTypeEnum{

    NONE("0","空闲"),
    PARK("1","泊车"),
    LOAD("2","装载"),
    UNLOAD("3","卸载"),
    WASTE("4","排土"),
    PETROL("5","加油"),
    REFILL("6","加水");

    private String value;

    private String desc;

    TaskTypeEnum(String value, String desc){
        this.value=value;
        this.desc=desc;
    }

    @JsonValue
    public String getValue(){
        return this.value;
    }

    public String getDesc(){
        return this.desc;
    }
}
