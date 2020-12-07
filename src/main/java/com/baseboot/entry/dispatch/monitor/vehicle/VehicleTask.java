package com.baseboot.entry.dispatch.monitor.vehicle;

import com.baseboot.common.service.DelayedService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.CollisionDetectUtil.Rectangle;
import com.baseboot.entry.dispatch.CalculatedValue;
import com.baseboot.entry.dispatch.monitor.LocationType;
import com.baseboot.entry.dispatch.path.GlobalPath;
import com.baseboot.entry.dispatch.path.VehicleTrail;
import com.baseboot.entry.dispatch.path.WorkPathInfo;
import com.baseboot.entry.map.Point;
import com.baseboot.entry.map.SemiStatic;
import com.baseboot.enums.ConnectionStateEnum;
import com.baseboot.interfaces.receive.MapReceive;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.interfaces.send.MapSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.calculate.CalculateConfig;
import com.baseboot.service.dispatch.helpers.HelpClazz;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleTask extends AbstractLocation implements Runnable, HelpClazz<VehicleTaskHelper> {

    /**
     * 任务执行时间间隔,ms
     */
    private final static Integer INTERVAL = 1000;

    /**
     * 心跳间隔
     */
    private final static Integer HEART_BEAT = 2000;


    private Integer vehicleId;

    private VehicleTaskHelper vehicleTaskHelper;

    private ConnectionStateEnum vehicleState = ConnectionStateEnum.OFFLINE;

    private boolean isStart = false;//是否启动车辆

    private boolean isRun = false;//是否需要运行定时任务

    private DelayedService.Task taskRun;//运行任务

    private float vehicleTailAxle = 1.1f;//后轴到车尾的距离

    private float vehicleWidth = 7.67f;//车宽

    private float vehicleLength = 12.18f;//车长

    public VehicleTask(Integer vehicleId) {
        this.vehicleId = vehicleId;
        setType(LocationType.MOVABLE);
    }

    /********************************定时任务**********************************/

    /**
     * 车辆定时任务
     */
    private void startTimer() {
        log.debug("[{}]车辆定时任务启动", vehicleId);
        if (null == taskRun) {
            taskRun = DelayedService.buildTask(this);
            taskRun.withNum(-1)
                    .withPrintLog(false)
                    .withAtOnce(true)
                    .withDesc(BaseUtil.format("车辆[{}]定时任务执行,调度状态[{}]", vehicleId, vehicleTaskHelper.getLiveInfo().getDispState()));
        }
        DelayedService.addTask(taskRun, INTERVAL);
    }

    /**
     * 主动执行任务，根据车辆状态执行任务
     */
    @Override
    public void run() {
        if (!getHelper().checkLink()) {
            //断开连接，并且在地图区域内，需要显示
            SemiStatic curArea = getHelper().getCurArea();
            if (null != curArea) {
                getHelper().getVehicleMonitorManager().refreshMonitorCache();
            }
            return;
        }
        heartbeat();
        if (isStart && getHelper().getVehicleMonitorManager().isSelfMode()) {
            boolean trajectory = getTrajectoryByIdx();
            if (!trajectory) {
                log.debug("车辆[{}]不允许下发轨迹!", vehicleId);
            }
        }
    }

    /**
     * 立即执行
     */
    public void atOnce() {
        taskRun.setNextTime(BaseUtil.getCurTime());
        DelayedService.updateTask(taskRun);
    }

    /**
     * 发送心跳
     */
    private void heartbeat() {
        CommSend.heartBeat(vehicleId);
    }


    /**
     * 发送请求轨迹的命令,true为执行获取轨迹
     * {@link MapReceive#getTrajectoryByIdx}
     */
    public boolean getTrajectoryByIdx() {
        Integer activateMapId = DispatchUtil.getActivateMapId();
        if (null == activateMapId) {
            log.debug("车辆[{}]活动地图不存在!", vehicleId);
            return false;
        }
        GlobalPath globalPath = BaseCacheUtil.getGlobalPath(vehicleId);
        if (null == globalPath) {
            log.debug("车辆[{}]轨迹路径不存在!", vehicleId);
            return false;
        }
        if (this.getHelper().getVehicleMonitorManager().isReceiveObstacle()) {
            log.debug("【{}】下发轨迹被障碍物限制，不允许下发!", vehicleId);
            return false;
        }
        WorkPathInfo workPathInfo = globalPath.getWorkPathInfo();
        boolean enableRunning = workPathInfo.getHelper().judgeEnableRunning(getCurLocation());
        if (enableRunning) {
            int nearestId = workPathInfo.getNearestId();
            int trailEndId = workPathInfo.getTrailEndId();
            if (nearestId >= trailEndId || (nearestId >= workPathInfo.getSectionPathEndId() || nearestId >= workPathInfo.getPathPointNum() - 1)) {
                VehicleTrail vehicleTrail = BaseCacheUtil.getVehicleTrail(vehicleId);
                if (null != vehicleTrail) {
                    log.debug("车辆[{}]最近点>=轨迹终点，不请求轨迹!,发送上一包数据.nearestId={},trailEndId={},oldTrailNum={}",
                            vehicleId, nearestId, trailEndId,vehicleTrail.getVertexNum());
                    MapReceive.sendTrail(vehicleTrail);
                    return true;
                } else {
                    if (trailEndId != 0) {
                        log.debug("车辆[{}]第一次不存在轨迹，必须发一次，才能进入轨迹判断,nearestId={}.", vehicleId, nearestId);
                        sendTrajectory(workPathInfo, activateMapId, 0);
                        return true;
                    } else {
                        return false;
                    }
                }
            }
            sendTrajectory(workPathInfo, activateMapId, nearestId);
            return true;
        }
        return false;
    }

    /**
     * 下发轨迹
     */
    private void sendTrajectory(WorkPathInfo workPathInfo, Integer activateMapId, int nearestId) {
        double curSpeed = this.getHelper().getCurSpeed();
        workPathInfo.setPlanCurSpeed(curSpeed);
        MapSend.getTrajectory(activateMapId, vehicleId, curSpeed, nearestId, workPathInfo.getTrailEndId(), getCurLocation());
    }

    @Override
    public Rectangle getOutline(double collisionDistance) {
        Point location = getCurLocation();
        double s = vehicleLength / 2 - vehicleTailAxle;
        //tcl 删
        s = 0;
        double x = location.getX() + s * Math.cos(Math.toRadians(location.getYawAngle()));
        double y = location.getY() + s * Math.sin(Math.toRadians(location.getYawAngle()));
        return new Rectangle(x, y, vehicleLength + collisionDistance * 2, vehicleWidth + collisionDistance * 2, location.getYawAngle());//外扩2米
    }

    /**
     * 获取当前位置
     */
    @Override
    public Point getCurLocation() {
        return vehicleTaskHelper.getCurLocation();
    }


    @Override
    public double followSafeDistance() {
        return 15;
    }

    /**
     * 长的一半
     */
    @Override
    public double stopSafePADistance() {
        return vehicleLength / 2 + CalculatedValue.COLLISION_DETECT_DISTANCE;
    }

    /**
     * 宽的一半
     */
    @Override
    public double stopSafeRLDistance() {
        return vehicleWidth / 2 + CalculatedValue.COLLISION_DETECT_DISTANCE;
    }

    @Override
    public Integer getUniqueId() {
        return this.vehicleId;
    }

    /**************************************属性 获取/设置****************************************/

    /**
     * 车辆启动
     */
    public void startVehicle() {
        if (isStart) {
            WorkPathInfo workPathInfo = BaseCacheUtil.getWorkPathInfo(vehicleId);
            if (null != workPathInfo) {
                workPathInfo.getHelper().pathRunning();
            }
        }
    }

    /**
     * 车辆停止
     */
    public void stopVehicle() {
        if (null != taskRun) {
            if (!isStart) {
                WorkPathInfo workPathInfo = BaseCacheUtil.getWorkPathInfo(vehicleId);
                if (null != workPathInfo) {
                    workPathInfo.setSendTrail(false);
                }
            }
        }
    }

    /**
     * 设置车辆状态
     */
    public void setVehicleState(ConnectionStateEnum vehicleState) {
        if (!this.vehicleState.equals(vehicleState)) {
            log.debug("车辆[{}]状态改变,【{}】", vehicleId, vehicleState.getDesc());
            if (ConnectionStateEnum.CONN.equals(vehicleState)) {
                super.connection();
            } else if (ConnectionStateEnum.OFFLINE.equals(vehicleState)) {
                super.disConnection();
            }
            this.vehicleState = vehicleState;
        }
    }


    /**
     * 修改车辆启动标志
     */
    public void changeStartFlag(boolean isStart) {
        log.debug("修改车辆启动标志:{}={}", vehicleId, isStart);
        this.isStart = isStart;
        if (!isStart) {
        }
    }

    /**
     * 修改车辆定时任务标志
     */
    public void changeRunFlag(boolean isRun) {
        setVehicleState(ConnectionStateEnum.CONN);
        if (this.isRun != isRun) {
            log.debug("修改车辆定时任务标志:{}={}", vehicleId, isRun);
            this.isRun = isRun;
            if (isRun) {
                taskRun = null;
                startTimer();
            } else {
                if (null != taskRun) {
                    taskRun.withExec(false);
                }
            }
        }
    }

    @Override
    public String toString() {
        VehicleLiveInfo vehicleLiveInfo = vehicleTaskHelper.getLiveInfo();
        return BaseUtil.format("vehicleId={},isStart={},taskSate={},dispState={},modeState={}",
                vehicleId, isStart, vehicleLiveInfo.getTaskState().get(), vehicleLiveInfo.getDispState(), vehicleLiveInfo.getModeState());
    }

    @Override
    public VehicleTaskHelper getHelper() {
        return this.vehicleTaskHelper;
    }

    @Override
    public void initHelper() {
        this.vehicleTaskHelper = new VehicleTaskHelper();
        this.vehicleTaskHelper.initHelpClazz(this);
    }


    public Integer getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Integer vehicleId) {
        this.vehicleId = vehicleId;
    }

    public boolean isStart() {
        return isStart;
    }


}
