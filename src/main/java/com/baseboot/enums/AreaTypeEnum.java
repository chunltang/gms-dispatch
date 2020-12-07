package com.baseboot.enums;

import com.baseboot.entry.dispatch.area.LoadArea;
import com.baseboot.entry.dispatch.area.TaskArea;
import com.baseboot.entry.dispatch.area.UnLoadMineralArea;
import com.baseboot.entry.dispatch.area.UnLoadWasteArea;
import com.baseboot.entry.global.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 区域类型枚举
 */
public enum AreaTypeEnum implements IEnum<String> {

    NONE_AREA("-1", "未知区域", null),
    BLANK("0", "空白区域", null),
    PARKING("1", "停车", null),
    LOAD_AREA("2", "装载", LoadArea.class),
    UNLOAD_MINERAL_AREA("3", "卸矿", UnLoadMineralArea.class),
    UNLOAD_WASTE_AREA("4", "排土", UnLoadWasteArea.class),
    PETROL_STATION("5", "加油", null),
    JUNCTION("6", "路口", null),
    SINGLE_ROAD("7", "单行路", null),
    DOUBLE_ROAD("8", "双行路", null),
    IMPASSABLE_AREA("9", "不可通行区域", null);


    private String value;

    private String desc;

    private Class<? extends TaskArea> taskAreaClass;

    private AreaTypeEnum(String value, String desc, Class<? extends TaskArea> taskAreaClass) {
        this.value = value;
        this.desc = desc;
        this.taskAreaClass = taskAreaClass;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    public Class<? extends TaskArea> getTaskAreaClass() {
        return taskAreaClass;
    }
}
