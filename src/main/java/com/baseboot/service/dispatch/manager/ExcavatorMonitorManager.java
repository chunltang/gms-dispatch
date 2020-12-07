package com.baseboot.service.dispatch.manager;

import com.baseboot.common.service.RedisService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.area.LoadArea;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorGpsInfo;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorHmiInfo;
import com.baseboot.entry.dispatch.monitor.excavator.ExcavatorTask;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.LogType;
import com.baseboot.entry.global.RedisKeyPool;
import com.baseboot.entry.map.OriginPoint;
import com.baseboot.entry.map.Point;
import com.baseboot.enums.ConnectionStateEnum;
import com.baseboot.enums.LoadAreaStateEnum;
import com.baseboot.service.BaseCacheUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 电铲上报状态管理
 */
@Data
@Slf4j
public class ExcavatorMonitorManager {

    private Integer excavatorId;

    private final ExcavatorTask excavatorTask;

    private volatile int preCommand = 0;
    private volatile int preGnssState = 0;

    private ExcavatorGpsInfo liveGpsInfo = new ExcavatorGpsInfo();
    private ExcavatorHmiInfo liveHmiInfo = new ExcavatorHmiInfo();


    /**
     * 运行轨迹
     */
    private List<Point> movingTrajectory = new ArrayList<>();

    private Point curPoint;

    public ExcavatorMonitorManager(ExcavatorTask excavatorTask) {
        this.excavatorId = excavatorTask.getExcavatorId();
        this.excavatorTask = excavatorTask;
    }

    /**
     * 更新GPS数据
     */
    public void updateGpsInfo(ExcavatorGpsInfo liveInfo) {
        liveInfo.setReceiveTime(BaseUtil.getCurTime());
        liveInfo.setLinkFlag(true);
        OriginPoint op = BaseCacheUtil.getOriginPoint();
        liveInfo.setDx(liveInfo.getDx() - op.getX());
        liveInfo.setDy(liveInfo.getDy() - op.getY());
        liveInfo.setDz(liveInfo.getDz() - op.getZ());
        liveInfo.setEx(liveInfo.getEx() - op.getX());
        liveInfo.setEy(liveInfo.getEy() - op.getY());
        liveInfo.setEz(liveInfo.getEz() - op.getZ());
        this.liveGpsInfo = liveInfo;
        Point newPoint = liveInfo.getCurPoint();
        if (distinct(newPoint)) {
            movingTrajectory.add(newPoint);
        }
        LogUtil.printLog(() -> {
            log.debug("【{}】更新GPS位置:{},{},{},{},{}",
                    liveInfo.getExcavatorId(),
                    liveInfo.getCurPoint(),
                    liveInfo.getBucketPoint(),
                    liveInfo.getUAngle(),
                    liveInfo.getPAngle(),
                    liveInfo.getState());
        }, "excavatorGps-" + liveInfo.getExcavatorId(), 5000);
        RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.GPS_EXCAVATOR_PREFIX + excavatorId, BaseUtil.toJson(liveInfo));
        int state = liveInfo.getState();
        if (state != 42) {
            LogUtil.printLog(() -> {
                String format = BaseUtil.format("【{}】电铲GPS状态异常[{}]，可能导致位置不准确!!!", excavatorId, liveInfo.getState());
                LogUtil.addLogToRedis(LogType.WARN, "gpsInfo-" + excavatorId, format);
                log.error(format);
            }, "updateGpsInfoState-" + excavatorId, 5000);

        } else if (preGnssState != state) {
            String format = BaseUtil.format("【{}】电铲GPS状态[{}]", excavatorId, liveInfo.getState());
            LogUtil.addLogToRedis(LogType.INFO, "gpsInfo-" + excavatorId, format);
        }
        this.preGnssState = state;
        this.curPoint = newPoint;
    }

    /**
     * 更新HMI数据
     */
    public void updateHmiInfo(ExcavatorHmiInfo liveInfo) {
        LogUtil.printLog(() -> {
            log.debug("【{}】更新HMI数据", liveInfo.getExcavatorId());
        }, "excavatorHmi-" + liveInfo.getExcavatorId(), 5000);
        excavatorTask.changeMonitorState(ConnectionStateEnum.CONN);
        swatchSate(liveInfo);
        liveInfo.setReceiveTime(BaseUtil.getCurTime());
        liveInfo.setLinkFlag(true);
        this.liveHmiInfo = liveInfo;
    }

    /**
     * 获取所有数据并清除
     */
    public List<Point> getMovingTrajectory() {
        List<Point> points = new ArrayList<>(this.movingTrajectory);
        movingTrajectory.clear();
        return points;
    }

    /**
     * 去重
     */
    private boolean distinct(Point newPoint) {
        if (BaseUtil.allObjNotNull(this.curPoint, newPoint)) {
            if (Math.abs(newPoint.getX() - curPoint.getX()) < 0.5 && Math.abs(newPoint.getY() - curPoint.getY()) < 0.5) {
                return false;
            }
        }
        return true;
    }

    /**
     * 电铲状态切换
     */
    public void swatchSate(ExcavatorHmiInfo liveInfo) {
        if (null != this.getLiveHmiInfo() &&
                (null == excavatorTask.getLoadArea())) {
            return;
        }
        LoadArea loadArea = excavatorTask.getLoadArea();
        if (null == loadArea) {
            return;
        }
        if (preCommand == liveInfo.getCommandNo()) {
            return;
        }
        preCommand = liveInfo.getCommandNo();
        String format = BaseUtil.format("【{}】电铲状态切换,指令编号:{}", liveInfo.getExcavatorId(), liveInfo.getCommandNo());
        LogUtil.addLogToRedis(LogType.LOAD_COMMAND_REQUEST, "excavator-" + excavatorId, format);
        switch (liveInfo.getCommandNo()) {
            case 0:
                log.debug("收到电铲【{}】心跳指令!", excavatorId);
                break;
            case 1:
                log.debug("收到电铲【{}】停工指令!", excavatorId);
                loadArea.taskSpotStop();//停工
                break;
            case 2:
                log.debug("收到电铲【{}】开工指令!", excavatorId);
                loadArea.taskSpotStart();//开工
                break;
            case 3:
                log.debug("收到电铲【{}】进车指令!", excavatorId);
                loadArea.loadAreaEntry();//进车
                //syncExcavatorState(LoadAreaStateEnum.RELEVANCE, true);
                //syncExcavatorState(LoadAreaStateEnum.PREPARE, true);
                break;
            case 4:
                log.debug("收到电铲【{}】取消进车指令!", excavatorId);
                loadArea.loadAreaEntryCancel();//取消进车
                break;
            case 5:
                log.debug("收到电铲【{}】开装指令!", excavatorId);
                loadArea.loadAreaWorkBegin();//开装
                //syncExcavatorState(LoadAreaStateEnum.WORKING, true);
                break;
            case 6:
                log.debug("收到电铲【{}】出车指令!", excavatorId);
                loadArea.loadAreaWorkDone();//出车
                break;
            case 20:
                log.debug("收到电铲【{}】紧急停车命令!", excavatorId);
                break;
            default:
                log.error("电铲上报状态没有对应处理逻辑!");
        }
        excavatorTask.updateSendMessage();
    }


    /**
     * 电铲状态测试方法
     */
    private void syncExcavatorState(LoadAreaStateEnum state, boolean delay) {
        LoadArea loadArea = excavatorTask.getLoadArea();
        if (null == loadArea) {
            return;
        }
        if (delay) {
            BaseUtil.TSleep(2000);
        }
        loadArea.setStatus(state);
    }
}
