package com.baseboot.service.dispatch.vehicle;

import com.baseboot.common.config.QueueConfig;
import com.baseboot.common.service.MqService;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.DateUtil;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.global.LimitQueue;
import com.baseboot.entry.global.LogType;
import com.baseboot.entry.global.Request;
import com.baseboot.enums.TaskCodeEnum;
import com.baseboot.enums.VehicleCommandEnum;
import com.baseboot.service.dispatch.helpers.VehicleTaskHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 车载下发指令管理
 */
@Slf4j
@Data
public class CommandStateManager {

    private final Integer vehicleId;

    private VehicleTaskHelper helper;

    private VehicleCommandEnum preCode;//前一个指令

    private volatile boolean test = false;

    private LimitQueue<VehicleCommandEnum> commandQueue = new LimitQueue<>(100);

    public CommandStateManager(VehicleTaskHelper helper) {
        this.helper = helper;
        this.vehicleId = helper.getVehicleId();
    }

    /**
     * 取消前置命令限制，慎用
     */
    public void cancelPreCode() {
        this.preCode = null;
    }

    public void issueCommand(VehicleCommandEnum command, byte[] bytes, boolean isPrint) {
        Request request = new Request().withRouteKey(command.getKey()).withBytes(bytes).withToWho(QueueConfig.REQUEST_COMM).withNeedPrint(isPrint);
        issueCommand(command, request);
    }

    public void issueCommand(VehicleCommandEnum command, boolean isPrint) {
        Request request = new Request().withRouteKey(command.getKey()).withToWho(QueueConfig.REQUEST_COMM).withNeedPrint(isPrint);
        issueCommand(command, request);
    }

    public void issueCommand(VehicleCommandEnum command, String message, boolean isPrint) {
        Request request = new Request().withRouteKey(command.getKey()).withMessage(message).withToWho(QueueConfig.REQUEST_COMM).withNeedPrint(isPrint);
        issueCommand(command, request);
    }

    private void issueCommand(VehicleCommandEnum command, Request request) {
        if (test) {
            if (!command.equals(VehicleCommandEnum.HEARTBEAT)) {
                log.warn("【{}】测试模式下，发送指令: 【{}】", vehicleId, command.getDesc());
                MqService.request(request);
                test = false;
                return;
            }
        }
        synchronized (vehicleId) {
            boolean flag = true;
            if (null != preCode && !ignorePreCommand(command)) {
                switch (command) {
                    case VEHAUTOSTANDBY://是指定命令，不下发
                        if (preCode.equals(VehicleCommandEnum.VEHAUTOUNLOAD) ||
                                preCode.equals(VehicleCommandEnum.TASKEMERGENCYPARKBYLINE)) {//接到待机指令，但是前一个是卸矿指令，不下发
                            //没有任务能下发
                            if (!helper.getInputStateManager().isDefaultInput()) {
                                flag = false;
                            }
                        }
                        break;
                    case VEHAUTOUNLOAD://不是指定命令，不下发
                        if (!(preCode.equals(VehicleCommandEnum.VEHAUTOSTANDBY) ||
                                preCode.equals(VehicleCommandEnum.VEHAUTOUNLOAD) ||
                                preCode.equals(VehicleCommandEnum.VEHAUTOTRAILFOLLOWING))) {//接到卸矿指令，但是前一个不是卸矿指令或待机，不下发
                            flag = false;
                        }
                        break;
                    case VEHAUTOTRAILFOLLOWING://不是指定命令，不下发
                        if (!(preCode.equals(VehicleCommandEnum.VEHAUTOTRAILFOLLOWING) ||
                                preCode.equals(VehicleCommandEnum.VEHAUTOSTANDBY) ||
                                preCode.equals(VehicleCommandEnum.VEHAUTOLOAD) ||
                                preCode.equals(VehicleCommandEnum.VEHAUTOSAFEPARKING))) {//接到轨迹跟随指令，但是前一个不是轨迹跟随或待机，不下发
                            flag = false;
                        }
                        break;
                    case VEHAUTOSAFEPARKING://不是指定命令，不下发
                        if (!(preCode.equals(VehicleCommandEnum.VEHAUTOTRAILFOLLOWING) ||
                                preCode.equals(VehicleCommandEnum.VEHAUTOSTANDBY) ||
                                preCode.equals(VehicleCommandEnum.VEHAUTOSAFEPARKING))) {//接到安全停车指令，但是前一个不是轨迹跟随或待机或安全停车，不下发
                            flag = false;
                        }
                        break;
                    case VEHAUTOCLEAREMERGENCYPARK://不是指定命令，不下发
                        if (!helper.getVehicleMonitorManager().isTaskCode(TaskCodeEnum.TASKEMERGENCYPARKBYLINE, TaskCodeEnum.TASKEMERGENCYPARKBYTRAJECTORY)) {//接到清除安全停车指令，但是前一个不是经济安全停车或待机，不下发
                            flag = false;
                        }
                        break;
                }
            }
            if (!flag) {
                log.debug("【{}】下发指令被拦截,command=【{}】,preCommand=【{}】", vehicleId, command.getDesc(), preCode.getDesc());
                return;
            }
            if (!ignorePreCommand(command)) {
                preCode = command;
            }
            if (request.isNeedPrint()) {
                log.debug("车辆【{}】下发指令：【{}】", vehicleId, command.getDesc());
                request.setNeedPrint(false);
            }

            if (!VehicleCommandEnum.HEARTBEAT.equals(command)) {
                String commandLog = BaseUtil.format("【{}】矿卡指令,指令类型:【{}】,报文时间:【{}】",
                        vehicleId, command.getDesc(), DateUtil.formatStringFullTime());
                LogUtil.addLogToFile(LogType.COMMAND, vehicleId, commandLog);
                commandQueue.add(command);
            }

            MqService.request(request);
        }
    }

    /**
     * 判断前几个命令中是否包含指定下发命令
     * */
    public boolean isContains(int limit, VehicleCommandEnum command) {
        if (commandQueue.limitContains(limit, command)) {
            log.warn("【{}】判断前几个命令中是否包含指定命令：{}", vehicleId, command.getDesc());
            return true;
        }
        return false;
    }

    /**
     * 忽略前置命令
     */
    private boolean ignorePreCommand(VehicleCommandEnum command) {
        return VehicleCommandEnum.VEHATUOMODE.equals(command) ||
                VehicleCommandEnum.VEHAUTOSTART.equals(command) ||
                VehicleCommandEnum.VEHAUTOSTOP.equals(command) ||
                VehicleCommandEnum.HEARTBEAT.equals(command);
    }
}
