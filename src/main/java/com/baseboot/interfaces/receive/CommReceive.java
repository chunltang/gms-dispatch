package com.baseboot.interfaces.receive;

import com.baseboot.common.config.QueueConfig;
import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorGpsInfo;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorHmiInfo;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorTask;
import com.baseboot.entry.dispatch.monitor.vehicle.*;
import com.baseboot.entry.global.*;
import com.baseboot.entry.map.OriginPoint;
import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.task.MongoStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

@Slf4j
@Component
public class CommReceive {

    private final static CommReceive instance = new CommReceive();

    private final static Object lock = new Object();

    public static void dispense(String message, String routeKey, String messageId) {
        Response response = new Response().withRouteKey(routeKey).withMessageId(messageId).withToWho(QueueConfig.RECEIVE_COMM);
        try {
            Method method = instance.getClass().getDeclaredMethod(routeKey, String.class, Response.class);
            method.setAccessible(true);
            method.invoke(instance, message, response);
        } catch (NoSuchMethodException e) {
            log.error("CommReceive has not [{}] method", routeKey, e);
        } catch (IllegalAccessException e) {
            log.error("CommReceive access error [{}] method", routeKey, e);
        } catch (InvocationTargetException e) {
            log.error("CommReceive call error [{}] method", routeKey, e);
        }
    }

    /**
     * 矿卡报文
     */
    public void vehMonitor(String message, Response response) {
        Monitor monitor = BaseUtil.toObjIEnum(message, Monitor.class);
        if (null != monitor) {
            VehicleTask task = BaseCacheUtil.getVehicleTask(monitor.getFromVakCode());
            if (null != task) {
                monitor.setVecTrouble(new DeviceTrouble[]{new DeviceTrouble(24001,1)});
                task.getHelper().getVehicleMonitorManager().updateLiveInfo(monitor);
                MongoStore.addToMongo(MongoKeyPool.VEHICLE_MONITOR_PREFIX+monitor.getFromVakCode(),monitor);
                LogUtil.printLog(() -> {
                    log.debug("【{}】矿卡报文,任务类型:【{}】,报文时间:【{}】,序列号:【{}】，位置:【{}】",
                            monitor.getFromVakCode(),
                            TaskCodeEnum.getEnum((String.valueOf(monitor.getCurrentTaskCode()))).getDesc(),
                            monitor.timeToString(),
                            monitor.getLockedDeviceCode(),
                            monitor.positionToString());
                }, "vehMonitor-" + monitor.getFromVakCode(), 1000);
            } else {
                LogUtil.printLog(() -> {
                    log.error("接入车编号不存在!,code={}", monitor.getFromVakCode());
                }, "vehMonitor-error-" + monitor.getFromVakCode(), 10000);
            }
        } else {
            log.error("车辆数据监听异常");
        }
    }


    /**
     * 挖掘机HMI信息
     */
    public void excavatorHmiMonitor(String message, Response response) {
        ExcavatorHmiInfo monitor = BaseUtil.toObj(message, ExcavatorHmiInfo.class);
        if (null != monitor) {
            ExcavatorTask excavator;
            if (!BaseCacheUtil.isExistExcavator(monitor.getExcavatorId())) {
                synchronized (lock) {
                    if (BaseCacheUtil.isExistExcavator(monitor.getExcavatorId())) {
                        excavator = BaseCacheUtil.getExcavator(monitor.getExcavatorId());
                    } else {
                        excavator = new ExcavatorTask(monitor.getExcavatorId());
                        BaseCacheUtil.addExcavator(excavator);
                    }
                }
            } else {
                excavator = BaseCacheUtil.getExcavator(monitor.getExcavatorId());
            }
            excavator.getMonitorManager().updateHmiInfo(monitor);
        }
    }

    /**
     * 挖掘机GPS信息
     */
    public void excavatorGpsMonitor(String message, Response response) {
        ExcavatorGpsInfo monitor = BaseUtil.toObj(message, ExcavatorGpsInfo.class);
        if (null != monitor) {
            OriginPoint originPoint = BaseCacheUtil.getOriginPoint();
            monitor.setExcavatorId(801);
            monitor.setLiveSign(145);
            monitor.setEx(originPoint.getX() + 483.213);
            monitor.setEy(originPoint.getY() + 315.131);
            monitor.setEz(originPoint.getZ() + 5.9121);
            monitor.setEAngle(150);
            monitor.setDx(originPoint.getX() + 470.121);
            monitor.setDy(originPoint.getY() + 326.123);
            monitor.setDz(originPoint.getZ() + 6.3123);
            monitor.setDAngle(150);
            monitor.setState(42);
            monitor.setPAngle(0.9);
            monitor.setUAngle(-0.1);

            ExcavatorTask excavator = BaseCacheUtil.getExcavator(monitor.getExcavatorId());
            if (null != excavator) {
                excavator.getMonitorManager().updateGpsInfo(monitor);
            }
        }
    }

    /**
     * 数据缓存
     */
    private void monitorCache(VehicleTask task, Monitor monitor) {
        Integer vehicleId = monitor.getFromVakCode();
        Monitor pre = task.getHelper().getLiveInfo().getMonitor();
        if (!BaseUtil.arrayNotNull(monitor.getVecObstacle()) && null != pre) {
            if (Math.abs(pre.getXworld() - monitor.getXworld()) < 0.3 && Math.abs(pre.getYworld() - monitor.getYworld()) < 0.3) {
                return;
            }
        }
        String key = RedisKeyPool.VAP_MONITOR_PREFIX + vehicleId;
        RedisService.listAdd(BaseConstant.KEEP_DB, key, BaseUtil.toJson(monitor));
        Long size = RedisService.listSize(BaseConstant.KEEP_DB, key);
        if (size > 6000) {
            RedisService.listTrim(BaseConstant.KEEP_DB, key, 2000, size);
        }
    }

    public void getMonitorCache() {
        List<String> list = RedisService.getList(BaseConstant.KEEP_DB, RedisKeyPool.VAP_MONITOR_PREFIX + 10006);
        if (null != list) {
            Collections.reverse(list);
            list = list.subList(8000, list.size());
            for (String s : list) {
                vehMonitor(s, null);
                BaseUtil.TSleep(200);
            }
        }
    }


    private void addObstacle(Monitor monitor) {
        Obstacle[] obstacles = new Obstacle[1];
        Obstacle obstacle = new Obstacle();
        obstacle.setId(0);
        obstacle.setX(372);
        obstacle.setY(299);
        obstacle.setZ(0.0D);
        obstacle.setLength(1);
        obstacle.setWidth(1);
        obstacle.setHeight(1);
        obstacle.setAngle(0);
        obstacle.setXSpeed(0.0D);
        obstacle.setYSpeed(0.0D);
        obstacle.setZSpeed(0.0D);
        obstacle.setGridNumber(10);
        List<Obstacle.Grid> grids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Obstacle.Grid grid = new Obstacle.Grid();
            grid.setX(372);
            grid.setY(299);
            grid.setZ(0);
            grids.add(grid);
        }
        obstacle.setGrids(grids);
        monitor.setVecObstacle(obstacles);
    }


    //@Scheduled(cron = "*/1 * * * * *")
    public void receiveExcavatorMsg() {
        ExcavatorHmiInfo hmiInfo = new ExcavatorHmiInfo();
        hmiInfo.setCommandNo(0);
        hmiInfo.setExcavatorId(801);
        hmiInfo.setLiveSign(111);
        excavatorHmiMonitor(BaseUtil.toJson(hmiInfo), null);
        OriginPoint originPoint = BaseCacheUtil.getOriginPoint();
        ExcavatorGpsInfo monitor = new ExcavatorGpsInfo();
        monitor.setExcavatorId(801);
        monitor.setLiveSign(145);
        monitor.setEx(originPoint.getX() + 483.213);
        monitor.setEy(originPoint.getY() + 315.131);
        monitor.setEz(originPoint.getZ() + 5.9121);
        monitor.setEAngle(150);
        monitor.setDx(originPoint.getX() + 470.121);
        monitor.setDy(originPoint.getY() + 326.123);
        monitor.setDz(originPoint.getZ() + 6.3123);
        monitor.setDAngle(150);

        monitor.setState(42);
        monitor.setPAngle(0.9);
        monitor.setUAngle(-0.1);
        excavatorGpsMonitor(BaseUtil.toJson(monitor), null);
    }
}
