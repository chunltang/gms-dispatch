package com.baseboot.service.dispatch.vehicle.commandHandle;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.entry.dispatch.monitor.vehicle.Obstacle;
import com.baseboot.entry.global.LogType;
import com.baseboot.entry.map.Point;
import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.interfaces.send.CommSend;
import com.baseboot.service.DispatchUtil;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import com.baseboot.service.dispatch.input.DefaultInput;
import com.baseboot.service.dispatch.input.DispatchInput;
import com.baseboot.service.dispatch.input.InputCache;
import com.baseboot.service.dispatch.task.TriggerTask;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 车辆上报障碍物
 */
@Data
@Slf4j
@MonitorAttrCommand
public class ObstacleCommand implements MonitorAttrHandle {

    private Integer vehicleId;

    private VehicleTaskHelper helper;

    private DispatchInput curInput;//收到障碍物时的输入

    private Obstacle[] obstacles;

    private Point endPoint;//收到障碍物时的任务终点

    private String obstacleTaskId;

    private List<Obstacle> obstacleList = new ArrayList<>();


    public ObstacleCommand(VehicleTaskHelper helper) {
        this.vehicleId = helper.getVehicleId();
        this.helper = helper;
        this.obstacleTaskId = "obstacle_taskId_" + this.vehicleId;
    }

    public void receiveCommand(Monitor monitor) {
        Obstacle[] obstacles = monitor.getVecObstacle();
        if (BaseUtil.arrayNotNull(obstacles)) {
            String format = BaseUtil.format("【{}】收到[障碍物],数量=[{}]", vehicleId, obstacles.length);
            log.warn(format);
            LogUtil.addLogToRedis(LogType.WARN, "monitor-" + vehicleId, format);
            if (!helper.getVehicleMonitorManager().isReceiveObstacle()) {
                String desc = BaseUtil.format("【{}】收到[障碍物],下发安全停车", vehicleId);
                LogUtil.addLogToRedis(LogType.WARN, "monitor-" + vehicleId, desc);
            }
            helper.getVehicleMonitorManager().setObstacleFlag(true);
            //checkObstacleDistance(obstacles);
            this.obstacles = obstacles;
            this.endPoint = InputCache.getEndPoint(vehicleId);
            DispatchInput inputType = helper.getInputStateManager().getCurInputType();
            if (!(inputType instanceof DefaultInput)) {
                this.curInput = helper.getInputStateManager().getCurInputType();
            }

            CommSend.vehAutoSafeParking(vehicleId);//停车
            for (Obstacle obstacle : obstacles) {
                log.warn("车辆【{}】障碍物栅格数=[{}]", vehicleId, (null == obstacle.getGrids()) ? 0 : obstacle.getGrids().size());
            }
            if (null == this.curInput || inputType instanceof DefaultInput) {
                return;
            }
            log.debug("【{}】绕障路径条件生成,输入模式:{}", vehicleId, this.curInput.getDesc());
            TriggerTask triggerTask = new TriggerTask(obstacleTaskId, 15000, 5000, () -> {
                boolean taskCode = helper.getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKSTANDBY, TaskCodeEnum.TASKNORMALPARKBYTRAJECTORY);
                if (taskCode && helper.getInputStateManager().isDefaultInput() && helper.getVehicleMonitorManager().isReceiveObstacle()) {
                    String obsDesc = BaseUtil.format("【{}】请求绕障路径", vehicleId);
                    log.warn(obsDesc);
                    LogUtil.addLogToRedis(LogType.WARN, "monitor-" + vehicleId, obsDesc);
                    InputCache.addEndPoint(vehicleId, endPoint);
                    DispatchInput input = helper.getVehicleStateManager().switchTask(vehicleId, curInput.getClass());
                    if (null != input) {
                        return input.createPath(vehicleId);
                    }
                }
                return false;
            });
            TaskCodeCommand.addTask(vehicleId, triggerTask);
        }
    }

    /**
     * 计算障碍物绝对距离
     */
    private void checkObstacleDistance(Obstacle[] obstacles) {
        for (Obstacle obs1 : obstacles) {
            for (Obstacle obs2 : obstacleList) {
                double distance = DispatchUtil.twoPointDistance(obs1.getX(), obs1.getY(), obs2.getX(), obs2.getY());
                if (distance > 0.3 && distance < 1) {
                    log.warn("障碍物绝对值计算:{},oldGridNum={},newGridNum={}", distance, obs2.getGrids().size(), obs1.getGrids().size());
                }
            }
        }
        obstacleList.addAll(Arrays.asList(obstacles));
    }

    /**
     * 清理障碍物信息
     */
    public void clearObstacleInfo() {
        this.curInput = null;
        this.obstacles = null;
        this.endPoint = null;
    }
}
