package com.baseboot.entry.dispatch.monitor.vehicle;

import com.baseboot.common.service.MongoService;
import com.baseboot.enums.DriveType2Enum;
import com.baseboot.enums.DriveTypeEnum;
import com.baseboot.enums.ManualStateEnum;
import com.baseboot.enums.WhetherEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Monitor implements Serializable {

    private String _id = MongoService.getObjectId();

    /**
     * 报文产生设备编号,代表了当前报文所属设备
     */
    private int msgProdDevCode;

    /**
     * 矿用自卸车编号(1-9999)
     */
    private int fromVakCode = 0;


    private int year;

    private int month;

    private int day;

    private int hour;

    private int minute;

    private Float second;

    /**
     * 锁定设备编号,VAK当前所执行任务的来源设备
     */

    private int lockedDeviceCode;

    /**
     * 监控数据类型,1级还是2级
     */

    private int monitorDataType;

    /**
     * 车辆模式编号
     */

    private int vakMode;

    /**
     * 当前任务编号
     */
    private int currentTaskCode;

    /**
     * 轨迹编号
     */
    private int trackCode;

    /**
     * 车载请求编号
     */

    private int vakRequestCode;

    /**
     * 车辆当前挡位
     */

    private int currentGear;

    /**
     * GNSS状态
     */
    private int gnssState;

    /**
     * 经度
     */

    private Double longitude;

    /**
     * 纬度
     */

    private Double latitude;

    /**
     * 大地坐标系x坐标 单位米
     */

    private Double xworld;

    /**
     * 大地坐标系y坐标 单位米
     */
    private Double yworld;

    private Double zworld;

    /**
     * 局部坐标系x坐标 单位米
     */
    private Double xLocality;

    /**
     * 局部坐标系y坐标 单位米
     */
    private Double yLocality;

    /**
     * 横摆角  单位度
     */

    private Double yawAngle;

    /**
     * 航向角  单位度
     */
    private Double navAngle;

    /**
     * 前轮转向角  单位度
     */

    private Double wheelAngle;

    /**
     * 车辆速度  单位m/s
     */

    private Double curSpeed;

    /**
     * 车辆加速度  单位度
     */

    private Double addSpeed;

    /**
     * 故障数量
     */
    private int countofTrouble;

    /**
     * 故障结构体数组
     */
    private DeviceTrouble[] vecTrouble = {};

    /**
     * 故障解析后的描述
     */
    private String[] troubleDesc;

    /**
     * 障碍物数量
     */
    private int countofObstacle;

    /**
     * 障碍物结构体数组
     */
    private Obstacle[] vecObstacle = {};

    /**
     * 实际方向盘转角  deg
     */
    private Double realSteerAngle;

    /**
     * 实际方向盘转速  deg/s
     */
    private Double realSteerRotSpeed;

    /**
     * 实际油门开度 %
     */
    private Double realAcceleratorRate;

    /**
     * 液压制动器主缸实际制动压力比例	%
     */
    private Double realHydBrakeRate;

    /**
     * 电磁涡流制动器实际激磁电流比例	%
     */
    private Double realElectricFlowBrakeRate;

    /**
     * 发动机状态
     */
    private int realMotorState;

    /**
     * 行车制动状态
     */
    private int realForwardBrakeState;

    /**
     * 电缓制动状态
     */
    private int realElectricBrakeState;

    /**
     * 停车制动状态
     */
    private int realParkingBrakeState;

    /**
     * 装载制动状态
     */
    private int realLoadBrakeState;

    /**
     * 发动机转速
     */
    private int realMotorRotSpeed;

    /**
     * 货舱状态
     */
    private int realHouseLiftRate;

    /**
     * 左转向灯状态
     */
    private int realTurnLeftlightState;

    /**
     * 右转向灯状态
     */
    private int realTurnRightlightState;

    /**
     * 近光灯状态
     */
    private int realNearLightState;

    /**
     * 示廓灯状态
     */
    private int realContourLightState;

    /**
     * 刹车灯状态
     */
    private int realBrakeLightState;

    /**
     * 紧急信号灯状态
     */
    private int realEmergencyLightState;

    /* ***************新增******************/

    /**
     * 发动机工作小时数
     */
    private int engineWorkHour;

    /**
     * 车斗载重
     */
    private double loadCapacity;

    /**
     * 燃油油量百分比
     */
    private int fuelPercentage;

    /**
     * Steering驾驶模式
     */
    private DriveType2Enum steeringMode = DriveType2Enum.MANUAL_DRIVE;

    /**
     * ECU驾驶模式
     */
    private DriveType2Enum ecuMode = DriveType2Enum.MANUAL_DRIVE;

    /**
     * 决策控制器驾驶模式
     */
    private DriveTypeEnum decisionMode = DriveTypeEnum.MANUAL_DRIVE;

    /**
     * 人工接管方式
     */
    private ManualStateEnum manualMode = ManualStateEnum.NONE_MODE;

    /**
     * 系统驾驶模式
     */
    private DriveTypeEnum systemMode = DriveTypeEnum.MANUAL_DRIVE;

    /**
     * 自动模式申请,1为申请
     */
    private WhetherEnum applyForAutoMode = WhetherEnum.NO;

    public String timeToString() {
        return this.year + "-" + this.month + "-" + this.day + " " + this.hour + ":" + this.minute + ":" + this.second;
    }

    public String positionToString() {
        return this.xworld + "," + this.yworld + "," + this.zworld + "," + this.yawAngle;
    }
}
