package com.baseboot.entry.dispatch;

public class TimerCommand {

    /**
     * 车辆命令定时器前缀
     */
    public final static String VEHICLE_COMMAND_PREFIX = "vehicle_command_";

    /**
     * 车停时必须取消调的定时任务_vehicleId
     */
    public final static String VEHICLE_TEMPORARY_TASK_PREFIX = "vehicle_temporary_task_";

    /**
     * 等待待机定时任务命令
     */
    public final static String VEHICLE_AUTO_STANDBY_COMMAND = VEHICLE_COMMAND_PREFIX + "auto_standby_";

    /**
     * 等待紧急停车定时任务命令
     */
    public final static String VEHICLE_EMERGENCY_PARKING_COMMAND = VEHICLE_COMMAND_PREFIX + "emergency_parking_";

    /**
     * 清除紧急停车定时任务命令
     */
    public final static String VEHICLE_CLEAR_EMERGENCY_PARKING_COMMAND = VEHICLE_COMMAND_PREFIX + "clear_emergency_parking_";

    /**
     * 原路径安全停车
     */
    public final static String VEHICLE_SAFE_STOP_COMMAND = VEHICLE_COMMAND_PREFIX + "safe_stop_";

    /**
     * 卸矿
     */
    public final static String VEHICLE_AUTO_UNLOAD_COMMAND = VEHICLE_COMMAND_PREFIX + "auto_unload_";

    /**
     * 装载
     */
    public final static String VEHICLE_AUTO_LOAD_COMMAND = VEHICLE_COMMAND_PREFIX + "auto_load_";

    /**
     * 关闭发动机
     */
    public final static String VEHICLE_AUTO_ENGINE_OFF_COMMAND = VEHICLE_COMMAND_PREFIX + "auto_engineOff_";

    /**
     * 开启发动机
     */
    public final static String VEHICLE_AUTO_ENGINE_ON_COMMAND = VEHICLE_COMMAND_PREFIX + "auto_engineOn_";

    /**
     * 自动模式授权
     */
    public final static String VEHICLE_AUTO_APPLY_FOR = "auto_apply_for_";

    /**
     * 手动模式授权
     */
    public final static String VEHICLE_MANUAL_APPLY_FOR = "manual_apply_for_";

    public final static String VEHICLE_NONE_APPLY_FOR = "none_apply_for_";

    /**
     * 路径请求消息
     */
    public final static String PATH_REQUEST_COMMAND = "path_request";

    /**
     * 地图加载
     */
    public final static String MAP_LOAD_COMMAND = "map_load";

    /**
     * 调度初始化
     */
    public final static String DISPATCH_INIT_COMMAND = "dispatch_init";

    /**
     * 全局路径拟合
     */
    public final static String FITTING_GLOBAL_PATH_COMMAND = "fittingGlobalPath";


    /**
     * 生成车辆临时任务id
     */
    public static String getTemporaryKey(Integer vehicleId, String suffix) {
        return VEHICLE_TEMPORARY_TASK_PREFIX + vehicleId + "_" + suffix;
    }
}
