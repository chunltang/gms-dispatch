package com.baseboot.service.dispatch.input;

import com.baseboot.entry.global.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

/**
 * 调度单元车辆状态
 */
@Slf4j
public enum DispatchUnitStateEnum implements IEnum<String> {

    GO_LOAD_QUEUE_POINT("1", "前往装载区排队点"),
    LOAD_WAIT_INTO_COMMAND("2", "装载区等待进车信号"),
    GO_LOAD_TASK_POINT("3", "前往装载点"),
    PREPARE_LOAD("4", "准备装载"),
    EXEC_LOAD("5", "执行装载..."),

    FREE("6", "空闲"),

    GO_UNLOAD_QUEUE_POINT("1", "前往卸载区排队点"),
    UNLOAD_WAIT_INTO_COMMAND("2", "卸载区等待进车信号"),
    GO_UNLOAD_TASK_POINT("3", "前往卸载点"),
    PREPARE_UNLOAD("4", "准备卸载"),
    EXEC_UNLOAD("5", "执行卸载...");

    /**
     1 前往排队点
     2 等待进车信号
     3 前往任务点
     4 准备执行任务
     5 执行任务...
     6 空闲
     7 正在生成路径
     8 交互式生成路径完成
     9 交互式路径运行
     10 交互式路径生成失败
     11 交互式路径中断
     * */

    /*PATH_CREATING("20", "正在生成路径"),
    PATH_CREATED_SUCESS("21", "交互式生成路径完成"),
    PATH_CREATED_ERROR("22", "调度单元车辆创建路径失败"),
    PATH_INTERRUPT("23", "路径运行中断");*/

    private String value;

    private String desc;

    DispatchUnitStateEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @JsonValue
    public String getValue() {
        return this.value;
    }

    public String getDesc() {
        return this.desc;
    }

    public static DispatchUnitStateEnum getEnum(String value) {
        for (DispatchUnitStateEnum anEnum : DispatchUnitStateEnum.values()) {
            if (anEnum.getValue().equals(value)) {
                return anEnum;
            }
        }
        log.error("调度单元车辆状态异常,没有对应枚举类型,value={}",value);
        return null;
    }

    /**
     * 判断空载、去装载
     */
    public static boolean isNoLoadState(DispatchUnitStateEnum state) {
        if (DispatchUnitStateEnum.GO_LOAD_QUEUE_POINT.equals(state) ||
                DispatchUnitStateEnum.LOAD_WAIT_INTO_COMMAND.equals(state) ||
                DispatchUnitStateEnum.GO_LOAD_TASK_POINT.equals(state) ||
                DispatchUnitStateEnum.PREPARE_LOAD.equals(state) ||
                DispatchUnitStateEnum.EXEC_LOAD.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * 判断重载、去卸载
     */
    public static boolean isLoadState(DispatchUnitStateEnum state) {
        if (DispatchUnitStateEnum.GO_UNLOAD_QUEUE_POINT.equals(state) ||
                DispatchUnitStateEnum.UNLOAD_WAIT_INTO_COMMAND.equals(state) ||
                DispatchUnitStateEnum.GO_UNLOAD_TASK_POINT.equals(state) ||
                DispatchUnitStateEnum.PREPARE_UNLOAD.equals(state) ||
                DispatchUnitStateEnum.EXEC_UNLOAD.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * 判断工作
     */
    public static boolean isWorkingState(DispatchUnitStateEnum state) {
        if (DispatchUnitStateEnum.EXEC_LOAD.equals(state) ||
                DispatchUnitStateEnum.EXEC_UNLOAD.equals(state)) {
            return true;
        }
        return false;
    }

    /**
     * 判断路径生成
     */
    /*public static boolean isPathState(DispatchUnitStateEnum state) {
        if (DispatchUnitStateEnum.PATH_CREATING.equals(state) ||
                DispatchUnitStateEnum.PATH_CREATED_SUCESS.equals(state) ||
                DispatchUnitStateEnum.PATH_CREATED_ERROR.equals(state) ||
                DispatchUnitStateEnum.PATH_INTERRUPT.equals(state)) {
            return true;
        }
        return false;
    }*/
}
