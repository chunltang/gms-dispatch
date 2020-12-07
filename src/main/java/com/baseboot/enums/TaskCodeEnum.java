package com.baseboot.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

/**
 * 当前上报任务编号
 * */
@Slf4j
public enum  TaskCodeEnum{
    NONE("-1","解析异常"),
    HEARTBEAT("0","心跳"),
    TASKSELFCHECK("1","自检"),
    TASKSILENCE("2","静默（人工）"),
    TASKLAUNCHMOTOR("3","启动发动机"),
    TASKSTATICTEST("4","静态测试"),
    TASKSTANDBY("5","待机"),
    TASKDRIVING("6","轨迹跟随"),
    UNKNOW("7","换挡"),
    TASKUNLOADSOIL("8","卸载"),
    TASKLOAD("9","装载"),
    TASKDATASAVE("10","驻车制动"),
    TASKCLOSEMOTOR("11","关闭发动机"),
    TASKREMOTECONTROL("12","远程遥控"),
    TASKNORMALPARKBYTRAJECTORY("13","安全停车"),
    TASKEMERGENCYPARKBYLINE("14","直线紧急停车"),//回正方向急停
    TASKEMERGENCYPARKBYTRAJECTORY("15","原路径紧急停车"),//保持方向急停
    MANUALCONTROL("20","人工接管"),
    DRIVERCONTROL("21","驾驶员驾驶");


    /**
     保留 0,
     自检 1,
     静默 2,
     发动机启动 3,
     静态测试 4,
     待机 5,
     轨迹跟随 6,
     换挡 7,
     卸载 8,
     装载 9,
     驻车 10,
     关闭发动机 11,
     远程遥控 12,
     安全停车  13,
     回正方向急停 14,
     保持方向急停 15 ,
     人工接管 20,
     驾驶员驾驶 21
     * */


    private String value;

    private String desc;

    TaskCodeEnum(String value, String desc){
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

    public static TaskCodeEnum getEnum(String value){
        for (TaskCodeEnum taskCodeEnum : TaskCodeEnum.values()) {
            if(taskCodeEnum.getValue().equals(value)){
                return taskCodeEnum;
            }
        }
        log.error("车辆当前上报任务编号没有对应枚举类型,{}",value);
        return TaskCodeEnum.NONE;
    }
}
