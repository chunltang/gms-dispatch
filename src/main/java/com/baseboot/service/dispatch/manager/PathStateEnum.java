package com.baseboot.service.dispatch.manager;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 路径状态
 * */
public enum PathStateEnum {


    PATH_CREATING("1","正在生成路径"),
    PATH_CREATED("2","路径生成完成"),
    PATH_RUNNING("3","路径运行"),
    PATH_CREATEd_ERROR("4","路径生成失败"),
    PATH_INTERRUPT("5","路径中断"),
    FREE("6","车辆空闲");

    private String value;

    private String desc;

    PathStateEnum(String value, String desc){
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
