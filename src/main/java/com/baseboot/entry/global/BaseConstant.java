package com.baseboot.entry.global;

public class BaseConstant {

    public final static int KEEP_DB = 0;
    public final static int MONITOR_DB = 1;

    public final static long REDIS_EXPIRATION_TIME = 60 * 60 * 1000;//redis过期时间

    public final static long VEHICLE_EXPIRATION_TIME = 6 * 1000;//车辆连接过期时间

    public final static String TEMP_DIR = "/temp";//临时文件存放目录

    public final static String MONITOR_MESSAGE_PREFIX = "monitor_";//通信监听数据前缀
    public final static String MONITOR_VEHICLE_MESSAGE_PREFIX = MONITOR_MESSAGE_PREFIX+"vehicle_";//通信监听数据前缀
    public final static String MONITOR_HMI_MESSAGE_PREFIX = MONITOR_MESSAGE_PREFIX+"hmi_";//通信监听数据前缀
    public final static String MONITOR_GPS_MESSAGE_PREFIX = MONITOR_MESSAGE_PREFIX+"gps_";//通信监听数据前缀

}
