package com.baseboot.service.dispatch.input;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.BaseCache;
import com.baseboot.entry.global.IEnum;
import com.baseboot.entry.global.Response;
import com.baseboot.entry.map.Point;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.manager.PathManager;
import com.baseboot.service.dispatch.manager.PathStateEnum;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认模式，什么都能做
 */
@Slf4j
public class DefaultInput implements DispatchInput {

    private Integer vehicleId;

    @Override
    public void setPathManager(Integer vehicleId, PathManager pathManager) {
        if (BaseUtil.allObjNotNull(vehicleId, pathManager)) {
            log.debug("[{}]设置默认调度输入!", vehicleId);
            InputCache.addPathManager(vehicleId, pathManager);
            InputCache.addDispatchInput(vehicleId, this);
            this.vehicleId = vehicleId;
            VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
            if(null!=vehicleTask){
                vehicleTask.getHelper().getTaskStateManager().changeToFreeState();
            }
        }
    }

    @Override
    public Point getEndPoint() {
        return null;
    }

    @Override
    public boolean startTask(Integer vehicleId, Response response) {
        return false;
    }

    @Override
    public boolean createPath(int planType) {
        return false;
    }

    @Override
    public void startRun() {
        log.debug("[{}]开始启动运行!", vehicleId);
        log.debug("【{}】当前默认输入源，不做操作!", vehicleId);
    }

    @Override
    public void stopRun() {
        log.debug("【{}】当前默认输入源，不做操作!", vehicleId);
        /*VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            vehicleTask.getHelper().getVehicleStateManager().stopTask();
        }*/
    }

    @Override
    public Enum getTaskState() {
        return null;
    }

    @Override
    public void startCreatePathNotify() {
        log.debug("[{}]收到开始生成路径通知[startCreatePath]!", vehicleId);
        log.debug("【{}】当前默认输入源，不做操作!", vehicleId);
    }

    @Override
    public void createPathSuccessNotify() {
        log.debug("[{}]收到路径生成成功通知[createPathSuccess]!", vehicleId);
        log.debug("【{}】当前默认输入源，不做操作!", vehicleId);
    }

    @Override
    public void createPathErrorNotify() {
        log.debug("[{}]收到路径生成异常通知[createPathError]!", vehicleId);
        log.debug("【{}】当前默认输入源，不做操作!", vehicleId);
    }

    @Override
    public void runErrorNotify() {
        log.debug("[{}]收到运行路径异常通知[runError]!", vehicleId);
        log.debug("【{}】当前默认输入源，不做操作!", vehicleId);
    }

    @Override
    public void startRunNotify() {
        log.debug("[{}]收到开始运行路径通知[startRun]!", vehicleId);
        log.debug("【{}】当前默认输入源，不做操作!", vehicleId);
    }

    @Override
    public void stopRunNotify() {
        log.debug("[{}]收到停止运行路径通知[stopRun]!", vehicleId);
        log.debug("【{}】当前默认输入源，不做操作!", vehicleId);
    }

    @Override
    public void arriveNotify() {
        log.debug("[{}]收到到达终点通知[arrive]!", vehicleId);
        log.debug("【{}】当前默认输入源，不做操作!", vehicleId);
    }

    @Override
    public Enum getTaskCodeEnum(String value) {
        return null;
    }

    @Override
    public String getTaskCode(Enum codeEnum) {
        return null;
    }

    @Override
    public boolean changeTaskState(IEnum state, IEnum[] expects) {
        return false;
    }

    @Override
    public String getDesc() {
        return "默认输入源";
    }
}
