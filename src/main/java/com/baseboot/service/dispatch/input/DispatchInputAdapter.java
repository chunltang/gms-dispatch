package com.baseboot.service.dispatch.input;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.VehicleTask;
import com.baseboot.entry.global.BaseCache;
import com.baseboot.entry.global.IEnum;
import com.baseboot.entry.global.LogType;
import com.baseboot.entry.global.Response;
import com.baseboot.entry.map.Point;
import com.baseboot.service.BaseCacheUtil;
import com.baseboot.service.dispatch.manager.PathManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
@Data
public class DispatchInputAdapter<T extends Enum> implements DispatchInput<T> {

    private Integer vehicleId;

    /**
     * 设置输入和路径的关联关系
     */
    public void setPathManager(Integer vehicleId, PathManager pathManager) {
        if (BaseUtil.allObjNotNull(vehicleId, pathManager)) {
            InputCache.addPathManager(vehicleId, pathManager);
            InputCache.addDispatchInput(vehicleId, this);
            this.vehicleId = vehicleId;
            log.debug("[{}]设置调度输入!", vehicleId);
        }
    }


    @Override
    public boolean createPath(int planType) {
        PathManager pathManager = InputCache.getPathManager(vehicleId);
        if (null != pathManager) {
            log.debug("[{}]开始创建路径!", vehicleId);
            Point endPoint = getEndPoint();
            if (null == endPoint) {
                log.error("车辆[{}]没有设置路径终点!", vehicleId);
                return false;
            }
            return pathManager.pathCreating(endPoint, planType);
        }
        return false;
    }

    @Override
    public Point getEndPoint() {
        return InputCache.getEndPoint(vehicleId);
    }

    @Override
    public boolean startTask(Integer vehicleId, Response response) {
        return false;
    }

    /**
     * 设置终点位置
     */
    public void setEndPoint(Point point) {
        if (BaseUtil.allObjNotNull(vehicleId, point)) {
            InputCache.addEndPoint(vehicleId, point);
            log.debug("[{}]设置终点!", vehicleId);
        }
        if (null == point) {
            InputCache.removeEndPoint(vehicleId);
        }
    }


    @Override
    public void startRun() {
        PathManager pathManager = InputCache.getPathManager(vehicleId);
        if (null != pathManager) {
            pathManager.pathRunning();
            log.debug("[{}]开始运行路径!", vehicleId);
        }
    }

    @Override
    public void stopRun() {
        setEndPoint(null);
        VehicleTask vehicleTask = BaseCacheUtil.getVehicleTask(vehicleId);
        if (null != vehicleTask) {
            vehicleTask.getHelper().getVehicleStateManager().stopTask();
        }
    }

    @Override
    public T getTaskState() {
        return null;
    }

    @Override
    public boolean changeTaskState(IEnum<String> state, IEnum<String>... expects) {
        PathManager pathManager = InputCache.getPathManager(vehicleId);
        if (null != pathManager) {
            if (pathManager.getHelper().getTaskStateManager().getTaskState().equals(state.getValue())) {
                return true;
            }
            String format = BaseUtil.format("【{}】当前调度任务：[{}]", getVehicleId(), state.getDesc());
            LogUtil.addLogToRedis(LogType.INFO, "task-" + getVehicleId(), format);
            if (BaseUtil.arrayNotNull(expects)) {
                String[] array = Arrays.stream(expects).map(IEnum::getValue).toArray(String[]::new);
                return pathManager.getHelper().getTaskStateManager().changeTaskStateEnum(state.getValue(), state.getDesc(), array);
            } else {
                return pathManager.getHelper().getTaskStateManager().changeTaskStateEnum(state.getValue(), state.getDesc());
            }
        }
        return false;
    }


    @Override
    public void startCreatePathNotify() {
        log.debug("[{}]收到开始生成路径通知[startCreatePath]!", vehicleId);
    }

    @Override
    public void createPathSuccessNotify() {
        log.debug("[{}]收到路径生成成功通知[createPathSuccess]!", vehicleId);
    }

    @Override
    public void createPathErrorNotify() {
        log.debug("[{}]收到路径生成异常通知[createPathError]!", vehicleId);
    }

    @Override
    public void runErrorNotify() {
        log.debug("[{}]收到运行路径异常通知[runError]!", vehicleId);
    }

    @Override
    public void startRunNotify() {
        log.debug("[{}]收到开始运行路径通知[startRun]!", vehicleId);
    }

    @Override
    public void stopRunNotify() {
        setEndPoint(null);
        log.debug("[{}]收到停止运行路径通知[stopRun]!", vehicleId);
    }

    @Override
    public void arriveNotify() {
        setEndPoint(null);
        log.debug("[{}]收到到达终点通知[arrive]!", vehicleId);
    }

    @Override
    public T getTaskCodeEnum(String value) {
        return null;
    }

    @Override
    public String getTaskCode(T codeEnum) {
        return null;
    }

    @Override
    public String getDesc() {
        return "";
    }


}
