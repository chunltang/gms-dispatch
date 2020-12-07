package com.baseboot.service.dispatch.input;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.map.Point;
import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.manager.PathManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class InputCache {
    /**
     * 所有路径管理对象
     */
    private static Map<Integer, PathManager> pathManagerMap = new ConcurrentHashMap<>();

    /**
     * 所有输入接收对象
     */
    private static Map<Integer, DispatchInput> dispatchInputMap = new ConcurrentHashMap<>();

    /**
     * 车子路径终点
     */
    private static Map<Integer, Point> endPointMap = new ConcurrentHashMap<>();

    /**
     * 车子路径起点
     */
    private static Map<Integer, Point> startPointMap = new ConcurrentHashMap<>();

    /**
     * 不是指定输入，则创建并返回，不然返回原输入
    public static synchronized <T extends DispatchInput> T inputSetAndGet(Integer vehicleId, Class<T> input) {
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if(null==vehicleTask){
            return null;
        }
        DispatchInput di = getDispatchInput(vehicleId);
        if (null == di || !di.getClass().equals(input)) {
            try {
                if ( !vehicleTask.getHelper().getRunStateManager().isStart()) {
                    di = input.newInstance();
                    vehicleTask.getHelper().getTaskStateManager().changeToFreeState();
                    vehicleTask.getHelper().getPathManager().initInput(di);
                    if(!vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKSTANDBY)){
                        CommSend.vehAutoSafeParking(vehicleId);
                    }
                    log.debug("调度输入源切换,result={}", result);
                } else {
                    log.error("车辆[【{}】切换调度输入源失败!", vehicleId);
                }
            } catch (Exception e) {
                log.error("反射生成对象异常!,车辆[{}]调度输入源切换失败", vehicleId, e);
            }
        }
        return (T) di;
    }

    *//**
     * 根据指定的输入类型删除
     *//*
    public static void inputGetAndDel(Integer vehicleId, Class<? extends DispatchInput> input) {
        DispatchInput di = getDispatchInput(vehicleId);
        if (null != di && di.getClass().equals(input)) {
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            if (null != vehicleTask) {
                vehicleTask.getHelper().getTaskStateManager().changeToFreeState();
                boolean result = vehicleTask.getHelper().getPathManager().initInput(new DefaultInput());
                if(result){
                    if(!vehicleTask.getHelper().getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKSTANDBY)){
                        CommSend.vehAutoSafeParking(vehicleId);
                    }
                }
                log.debug("调度输入源切换为默认输入[空闲],result={}", result);
            }
        }
    }*/

    public static void addPathManager(Integer vehicleId, PathManager pathManager) {
        if (BaseUtil.allObjNotNull(vehicleId, pathManager)) {
            pathManagerMap.put(vehicleId, pathManager);
        }

    }

    public static PathManager getPathManager(Integer vehicleId) {
        return pathManagerMap.get(vehicleId);
    }


    public static void addDispatchInput(Integer vehicleId, DispatchInput input) {
        if (BaseUtil.allObjNotNull(vehicleId, input)) {
            dispatchInputMap.put(vehicleId, input);
        }
    }

    public static DispatchInput getDispatchInput(Integer vehicleId) {
        return dispatchInputMap.get(vehicleId);
    }

    /**
     * 终点操作
     * */
    public static void addEndPoint(Integer vehicleId, Point point) {
        if (BaseUtil.allObjNotNull(vehicleId, point)) {
            endPointMap.put(vehicleId, point);
        }
    }

    public static void removeEndPoint(Integer vehicleId) {
        endPointMap.remove(vehicleId);
    }


    public static Point getEndPoint(Integer vehicleId) {
        return endPointMap.get(vehicleId);
    }

    /**
     * 起点操作
     * */
    public static void addStartPoint(Integer vehicleId, Point point) {
        if (BaseUtil.allObjNotNull(vehicleId, point)) {
            startPointMap.put(vehicleId, point);
        }
    }

    public static void removeStartPoint(Integer vehicleId) {
        startPointMap.remove(vehicleId);
    }


    public static Point getStartPoint(Integer vehicleId) {
        return startPointMap.get(vehicleId);
    }

}
