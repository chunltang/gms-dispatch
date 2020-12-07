package com.baseboot.service.dispatch.input;

import com.baseboot.entry.global.IEnum;
import com.baseboot.entry.global.Response;
import com.baseboot.entry.map.Point;
import com.baseboot.service.dispatch.manager.PathManager;

/**
 * 调度输入
 */
public interface DispatchInput<T extends Enum> {

    void setPathManager(Integer vehicleId, PathManager pathManager);

    /**
     * 车辆运行终点位置
     */
    Point getEndPoint();

    boolean startTask(Integer vehicleId, Response response);


    /**
     * 生成路径
     */
    boolean createPath(int planType);

    /**
     * 开始运行
     */
    void startRun();

    /**
     * 停止运行
     */
    void stopRun();

    boolean changeTaskState(IEnum<String> state,IEnum<String> ...expects);

    T getTaskState();

    /**
     * 开始生成路径通知
     */
    void startCreatePathNotify();

    /**
     * 路径生成成功通知
     */
    void createPathSuccessNotify();

    /**
     * 路径生成异常通知
     */
    void createPathErrorNotify();

    /**
     * 运行异常通知
     */
    void runErrorNotify();

    /**
     * 开始运行通知
     */
    void startRunNotify();

    /**
     * 停止通知，未到达终点
     */
    void stopRunNotify();

    /**
     * 到达终点通知
     */
    void arriveNotify();

    T getTaskCodeEnum(String value);

    String getTaskCode(T codeEnum);

    String getDesc();

}
