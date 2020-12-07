package com.baseboot.service.dispatch.manager;

import com.baseboot.entry.dispatch.monitor.vehicle.VehicleLiveInfo;
import com.baseboot.entry.map.Point;
import com.baseboot.interfaces.send.MapSend;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.input.DispatchInput;
import com.baseboot.service.dispatch.input.InputCache;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * 路径管理
 */
@Slf4j
@Data
public class PathManager {

    private VehicleTaskHelper helper;

    private Integer vehicleId;

    private DispatchInput input;//默认交互式

    private PathStateEnum pathState = PathStateEnum.FREE;

    public PathManager(VehicleTaskHelper helper) {
        this.helper = helper;
        this.vehicleId = helper.getVehicleId();
    }

    public void initInput(DispatchInput input) {
        this.input = input;
        this.input.setPathManager(vehicleId, this);
    }

    /**
     * 判断是否是可以创建路径
     */
    public boolean canCreatePath() {
        return isValState(PathStateEnum.PATH_CREATED, PathStateEnum.PATH_CREATEd_ERROR, PathStateEnum.FREE, PathStateEnum.PATH_INTERRUPT);
    }

    public boolean isValState(PathStateEnum... values) {
        return Arrays.asList(values).contains(pathState);
    }


    public void changePathState(PathStateEnum pathState) {
        helper.getVehicleMonitorManager().getVehicleLiveInfo().setPathState(pathState);
        this.pathState = pathState;
    }

    /**
     * 清除状态
     */
    public void clearPathWebParam() {
        VehicleLiveInfo liveInfo = helper.getVehicleMonitorManager().getVehicleLiveInfo();
        if (PathStateEnum.PATH_CREATEd_ERROR.equals(pathState)) {
            liveInfo.setPathState(PathStateEnum.FREE);
        }
        liveInfo.setPathError(PathErrorEnum.NONE);
    }

    /**
     * 设置路径异常信息
     */
    private void setPathErrorCode(PathErrorEnum pathError) {
        helper.getVehicleMonitorManager().getVehicleLiveInfo().setPathError(pathError);
    }

    /**
     * 开始路径生成
     */
    public boolean pathCreating(Point point, int planType) {
        synchronized (BaseCacheUtil.objectLock(String.valueOf(vehicleId))){
            if (!helper.getVehicleStateManager().startCreatePath()) {
                log.debug("车辆[{}]当前没有调度输入!", vehicleId);
                return false;
            }
            if (!BaseCacheUtil.removeGlobalPath(helper.getVehicleId())) {
                log.debug("车辆[{}]路径请求失败，车辆正在运行状态!", vehicleId);
                return false;
            }
            clearInterrupt();
            if (!canCreatePath()) {
                log.debug("车辆[{}]当前路径状态:{},不能提交", vehicleId, pathState.getDesc());
                return false;
            }
            Point curLocation = helper.getCurLocation();
            InputCache.addStartPoint(vehicleId, curLocation);
            MapSend.getGlobalPath(helper.getVehicleTask(), helper.getVehicleId(), curLocation, point, planType);
            changePathState(PathStateEnum.PATH_CREATING);
            log.debug("车辆[{}]生成路径请求请求中", vehicleId);
            input.startCreatePathNotify();
            return true;
        }
    }

    /**
     * 路径生成完成
     */
    public boolean pathCreated() {
        if (!helper.getVehicleStateManager().createSuccess()) {
            log.debug("车辆[{}]当前没有调度输入!", vehicleId);
            return false;
        }
        if (!PathStateEnum.PATH_CREATING.equals(pathState)) {
            log.error("车辆[{}]不是正在生成路径状态,不能改为路径成完成状态", helper.getVehicleId());
            return false;
        }
        changePathState(PathStateEnum.PATH_CREATED);
        input.createPathSuccessNotify();
        return true;
    }

    /**
     * 路径运行
     */
    public boolean pathRunning() {
        if (helper.getVehicleStateManager().isFreeState()) {
            log.debug("车辆[{}]空闲,不能运行路径", vehicleId);
            return false;
        }
        if (!(PathStateEnum.PATH_CREATED.equals(pathState) || (PathStateEnum.PATH_INTERRUPT.equals(pathState)))) {
            log.error("车辆[{}]不是路径生成或中断状态，不能运行路径!", helper.getVehicleId());
            return false;
        }
        changePathState(PathStateEnum.PATH_RUNNING);
        helper.getVehicleTask().changeStartFlag(true);
        helper.getVehicleTask().startVehicle();
        input.startRunNotify();
        return true;
    }

    /**
     * 路径生命周期中断，停止
     */
    public boolean pathInterrupt() {
        changePathState(PathStateEnum.PATH_INTERRUPT);
        helper.getVehicleTask().changeStartFlag(false);
        helper.getVehicleTask().stopVehicle();
        InputCache.removeStartPoint(vehicleId);
        BaseCacheUtil.removeGlobalPath(helper.getVehicleId());
        input.stopRunNotify();
        return true;
    }

    /**
     * 清理路径中断状态
     */
    public boolean clearInterrupt() {
        boolean start = helper.getVehicleTask().isStart();
        if (!start && PathStateEnum.PATH_INTERRUPT.equals(pathState)) {
            log.debug("车辆[{}]清理路径中断状态", vehicleId);
            changePathState(PathStateEnum.FREE);
            helper.getVehicleTask().stopVehicle();
            InputCache.removeStartPoint(vehicleId);
            BaseCacheUtil.removeGlobalPath(helper.getVehicleId());
        }
        return !start;
    }


    /**
     * 路径运行完成
     */
    public boolean pathRunEnd() {
        if (!PathStateEnum.PATH_RUNNING.equals(pathState) && !PathStateEnum.FREE.equals(pathState)) {
            log.error("车辆[{}]不是路径运行状态，不能变更为运行完成状态!", helper.getVehicleId());
            return false;
        }
        pathClear();
        return true;
    }

    /**
     * 清理参数
     * */
    public void pathClear(){
        //清理路径信息
        InputCache.removeStartPoint(vehicleId);
        changePathState(PathStateEnum.FREE);
        helper.getVehicleTask().changeStartFlag(false);
        helper.getVehicleTask().stopVehicle();
        BaseCacheUtil.removeGlobalPath(helper.getVehicleId());
        input.arriveNotify();
    }

    /**
     * 路径生成异常
     */
    public boolean pathCreateError(PathErrorEnum pathError) {
        if (!helper.getVehicleStateManager().createPathError()) {
            log.debug("车辆[{}]当前没有调度输入!", vehicleId);
            return false;
        }
        if (!PathStateEnum.PATH_CREATING.equals(pathState)) {
            log.debug("车辆[{}]不是路径生成状态，不能改为路径生成异常!", helper.getVehicleId());
            return false;
        }

        setPathErrorCode(pathError);
        changePathState(PathStateEnum.PATH_CREATEd_ERROR);
        helper.getVehicleMonitorManager().refreshMonitorCache();//发送数据
        clearPathWebParam();

        //路径生成失败，转为空闲状态
        log.error("车辆[{}]路径生成异常,codeDesc=【{}】", helper.getVehicleId(),pathError.getDesc());
        InputCache.removeStartPoint(vehicleId);
        BaseCacheUtil.removeGlobalPath(helper.getVehicleId());

        input.createPathErrorNotify();
        return true;
    }

    public VehicleTaskHelper getHelper() {
        return helper;
    }

    @Override
    public String toString() {
        return "" + this.helper.getVehicleId();
    }
}
