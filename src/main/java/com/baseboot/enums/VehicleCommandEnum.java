package com.baseboot.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum VehicleCommandEnum {

    HEARTBEAT("VehHeartbeat2","心跳指令"),
    TASKEMERGENCYPARKBYLINE("VehAutoEmergencyParking","直线紧急停车"),//回正方向急停
    VEHAUTOCLEAREMERGENCYPARK("VehAutoClearEmergencyPark","清除紧急停车指令"),
    VEHAUTOSTART("VehAutoStart","车辆启动发动机指令"),
    VEHAUTOSTOP("VehAutoStop","车辆停止发动机指令"),
    VEHAUTOSTANDBY("VehAutoStandby","待机指令"),
    VEHAUTOSAFEPARKING("VehAutoSafeParking","原路径安全停车指令"),
    VEHAUTOTRAILFOLLOWING("VehAutoTrailFollowing","轨迹跟随指令"),
    VEHAUTOLOADBRAKE("VehAutoLoadBrake","装载制动指令"),
    VEHAUTOUNLOAD("VehAutoUnload","卸矿指令"),
    VEHAUTODUMP("VehAutoDump","排土指令"),
    VEHAUTOLOAD("VehAutoLoad","装载指令"),
    VEHATUOMODE("VehAutoMode","授权自动模式指令");

    private String key;

    private String desc;

    VehicleCommandEnum(String key, String desc){
        this.key =key;
        this.desc=desc;
    }

    @JsonValue
    public String getKey(){
        return this.key;
    }

    public String getDesc(){
        return this.desc;
    }
}
