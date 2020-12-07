package com.baseboot.entry.dispatch.monitor.excavator;

import com.baseboot.common.service.DelayedService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.CollisionDetectUtil.Rectangle;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.area.LoadArea;
import com.baseboot.entry.dispatch.monitor.vehicle.AbstractLocation;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleLiveInfo;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.map.OriginPoint;
import com.baseboot.entry.map.Point;
import com.baseboot.entry.map.SemiStatic;
import com.baseboot.enums.AreaTypeEnum;
import com.baseboot.enums.ConnectionStateEnum;
import com.baseboot.enums.LoadAreaStateEnum;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.dispatch.input.DispatchUnitInput;
import com.baseboot.service.dispatch.manager.ExcavatorMonitorManager;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 挖掘机
 */
@Slf4j
public class ExcavatorTask extends AbstractLocation implements Runnable {

    private Integer excavatorId;

    private ExcavatorMonitorManager monitorManager;

    private Random signRandom = new Random();

    /**
     * 任务执行时间间隔,ms
     */
    private final static Integer INTERVAL = 200;

    /**
     * 在那个装载区
     */
    private LoadArea loadArea;

    /**
     * 在那个地图区域
     */
    private SemiStatic curArea;

    /**
     * 运行任务
     */
    private DelayedService.Task taskRun;

    private ExcavatorSendMessage sendMessage;

    private ConnectionStateEnum monitorState = ConnectionStateEnum.OFFLINE;

    private int calculateStep = 0;

    public ExcavatorTask(Integer excavatorId) {
        this.excavatorId = excavatorId;
        this.sendMessage = new ExcavatorSendMessage();
        this.monitorManager = new ExcavatorMonitorManager(this);
        BaseUtil.timer(this::startTimer, 1000);
    }

    public void changeMonitorState(ConnectionStateEnum monitorState) {
        if (!monitorState.equals(this.monitorState)) {
            synchronized (BaseCacheUtil.objectLock("changeMonitorState-" + excavatorId)) {
                if (!monitorState.equals(this.monitorState)) {
                    log.debug("【{}】电铲状态变更:【{}】", excavatorId, monitorState.getDesc());
                    this.monitorState = monitorState;
                }
                if (monitorState.equals(ConnectionStateEnum.CONN)) {
                    super.connection();
                } else {
                    super.disConnection();
                }
            }
        }
    }

    /**
     * tcl 在那个装载区域,挖掘机可能不在地图范围之内，需要绑定装载区
     */
    public SemiStatic getArea() {
        SemiStatic innerArea = DispatchUtil.isInnerArea(getCurLocation(), AreaTypeEnum.LOAD_AREA);
        if (null != innerArea) {//开始时在装载区内，挖着挖着出了装载区，但是还是沿用之前所在装载区
            this.curArea = innerArea;
        }
        return this.curArea;
    }

    public LoadArea getLoadArea() {
        /*SemiStatic aStatic = getArea();
        if (null != aStatic) {
            TaskArea taskArea = BaseCacheUtil.getTaskArea(aStatic.getId());
            if (null == taskArea) {
                return null;
            }
            if (taskArea instanceof LoadArea) {
                this.loadArea = (LoadArea) taskArea;
                return this.loadArea;
            }
        }*/
        //只有一个装载区的情况，直接绑定
        List<LoadArea> taskAreas = BaseCacheUtil.getTaskArea(AreaTypeEnum.LOAD_AREA);
        if (taskAreas.size() == 1) {
            this.loadArea = taskAreas.get(0);
            return this.loadArea;
        }
        return null;
    }

    /**
     * 车辆定时任务
     */
    private void startTimer() {
        log.debug("[{}]电铲定时任务启动", excavatorId);
        if (null == taskRun) {
            taskRun = DelayedService.buildTask(this);
            taskRun.withNum(-1)
                    .withPrintLog(false)
                    .withAtOnce(true)
                    .withDesc(BaseUtil.format("电铲[{}]定时任务执行", excavatorId));
        }
        DelayedService.addTask(taskRun, INTERVAL);
    }

    /**
     * 跟新下发数据
     */
    public void updateSendMessage() {
        this.sendMessage.getVehicles().clear();
        this.sendMessage.setExcavatorId(excavatorId);
        LoadAreaStateEnum status = this.loadArea.getStatus();
        this.sendMessage.setState(Integer.valueOf(status.getValue()));
        this.sendMessage.setSign(signRandom.nextInt(65000));
        this.sendMessage.setCode(0);
        OriginPoint originPoint = BaseCacheUtil.getOriginPoint();
        this.sendMessage.setOx(originPoint.getX());
        this.sendMessage.setOy(originPoint.getY());
        this.sendMessage.setOz(originPoint.getZ());

        Point curLocation = getCurLocation();
        this.sendMessage.setEx(curLocation.getX());
        this.sendMessage.setEy(curLocation.getY());
        this.sendMessage.setEz(curLocation.getZ());
        this.sendMessage.setEAngle(curLocation.getYawAngle());

        Point loadLocation = getLoadLocation();
        this.sendMessage.setDx(loadLocation.getX());
        this.sendMessage.setDy(loadLocation.getY());
        this.sendMessage.setDz(loadLocation.getZ());
        this.sendMessage.setDAngle(loadLocation.getYawAngle());

        this.sendMessage.setRelevanceId(null == loadArea.getVehicleId() ? 0 : loadArea.getVehicleId());
        Set<VehicleTask> vehicles = getInCurAreaVehicles();
        this.sendMessage.setSize(vehicles.size());
        if (vehicles.size() > 0) {
            ExcavatorSendMessage.VehicleInfo vehicleInfo;
            for (VehicleTask task : vehicles) {
                VehicleLiveInfo liveInfo = task.getHelper().getVehicleMonitorManager().getVehicleLiveInfo();
                vehicleInfo = new ExcavatorSendMessage.VehicleInfo();
                vehicleInfo.setVehicleId(task.getVehicleId());
                vehicleInfo.setDispatchState(Integer.valueOf(liveInfo.getDispState().getValue()));
                vehicleInfo.setDispatchTaskState(Integer.valueOf(liveInfo.getTaskState().get()));
                vehicleInfo.setDispatchModeState(Integer.valueOf(liveInfo.getModeState().getValue()));
                Monitor monitor = liveInfo.getMonitor();
                if (null != monitor) {
                    vehicleInfo.setVehicleModeState(monitor.getCurrentTaskCode());
                    vehicleInfo.setVehicleTaskState(Integer.valueOf(monitor.getDecisionMode().getValue()));
                }
                Point location = task.getCurLocation();
                vehicleInfo.setX(location.getX());
                vehicleInfo.setY(location.getY());
                vehicleInfo.setZ(location.getZ());
                vehicleInfo.setYawAngle(location.getYawAngle());
                vehicleInfo.setSpeed(task.getHelper().getCurSpeed());
                vehicleInfo.setArriveTime(0);//tcl 预计到达时间计算
                this.sendMessage.getVehicles().add(vehicleInfo);
            }
        }
        CommSend.excavatorSend(this.sendMessage);
    }

    /**
     * 获取在当前装载区域的所有车
     */
    private Set<VehicleTask> getInCurAreaVehicles() {
        Set<VehicleTask> result = new HashSet<>();
        Collection<VehicleTask> vehicleTasks = BaseCacheUtil.getVehicleTasks();
        for (VehicleTask vehicleTask : vehicleTasks) {
            SemiStatic curArea = vehicleTask.getHelper().getCurArea();
            if (null != curArea && null != this.loadArea) {
                //在区域内
                if (curArea.getId().equals(this.loadArea.getTaskAreaId())) {
                    result.add(vehicleTask);
                    break;
                }
                //判断装载和路径终点距离
                if (vehicleTask.getHelper().getInputStateManager().isGetInputType(DispatchUnitInput.class) &&
                        !vehicleTask.getHelper().getDispatchStateManager().isDispatchLoadState()) {
                    Double distance = vehicleTask.getHelper().getRunStateManager().getEndPointDistance();
                    if (null != distance && vehicleTask.getHelper().getRunStateManager().getEndPointDistance() < 20) {
                        result.add(vehicleTask);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 发送心跳数据
     */
    @Override
    public void run() {
        if (!checkLink()) {
            return;
        }
        if (calculateStep == 0) {
            getLoadArea();
            if (null == this.loadArea) {//tcl 电铲怎么绑定装载区，挖着挖着跑到地图外面去了
                LogUtil.printLog(() -> {
                    log.error("【{}】电铲没有绑定装载区", excavatorId);
                }, "excavatorTask-run-" + excavatorId, 5000);
                return;
            }
        }
        calculateStep++;
        if (calculateStep > 5) {
            calculateStep = 0;
        }
        updateSendMessage();
    }

    /**
     * 判断是否连接
     */
    public boolean checkLink() {
        ExcavatorHmiInfo liveHmiInfo = this.getMonitorManager().getLiveHmiInfo();
        if (null == liveHmiInfo) {
            return false;
        }
        long receiveTime = liveHmiInfo.getReceiveTime();
        return BaseUtil.getCurTime() - receiveTime <= BaseConstant.VEHICLE_EXPIRATION_TIME;
    }


    /**
     * 获取装在位置(铲斗)
     */
    public Point getLoadLocation() {
        return this.monitorManager.getLiveGpsInfo().getBucketPoint();
    }

    @Override
    public Rectangle getOutline(double collisionDistance) {
        Point location = getCurLocation();
        return new Rectangle(location.getX(), location.getY(), 10, 10, location.getYawAngle());//外扩2米
    }

    /**
     * 获取电铲位置
     */
    @Override
    public Point getCurLocation() {
        return this.monitorManager.getLiveGpsInfo().getCurPoint();
    }

    @Override
    public double followSafeDistance() {
        return 0;
    }

    @Override
    public double stopSafePADistance() {
        return 0;
    }

    @Override
    public double stopSafeRLDistance() {
        return 0;
    }

    @Override
    public Integer getUniqueId() {
        return this.excavatorId;
    }

    public Integer getExcavatorId() {
        return excavatorId;
    }

    public void setExcavatorId(Integer excavatorId) {
        this.excavatorId = excavatorId;
    }

    public ExcavatorMonitorManager getMonitorManager() {
        return monitorManager;
    }

    public void setMonitorManager(ExcavatorMonitorManager monitorManager) {
        this.monitorManager = monitorManager;
    }

    public void setLoadArea(LoadArea loadArea) {
        this.loadArea = loadArea;
    }

    public SemiStatic getCurArea() {
        return curArea;
    }

    public void setCurArea(SemiStatic curArea) {
        this.curArea = curArea;
    }

    public ExcavatorSendMessage getSendMessage() {
        return sendMessage;
    }

    public void setSendMessage(ExcavatorSendMessage sendMessage) {
        this.sendMessage = sendMessage;
    }

    public ConnectionStateEnum getMonitorState() {
        return monitorState;
    }
}
