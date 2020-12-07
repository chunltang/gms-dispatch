package com.baseboot.entry.dispatch.area;

import com.baseboot.entry.global.EventType;
import com.baseboot.enums.AreaTypeEnum;
import lombok.Data;

import java.util.Arrays;

@Data
public class UnLoadMineralArea extends UnloadArea {

    public UnLoadMineralArea(){
        this.setUnloadType(AreaTypeEnum.UNLOAD_MINERAL_AREA);
    }

    @Override
    public Integer getTaskAreaId() {
        return getUnloadAreaId();
    }

    @Override
    public AreaTypeEnum getAreaType() {
        return getUnloadType();
    }

    /**
     * 卸矿区状态改变
     * */
    @Override
    public void eventPublisher(EventType eventType, Object value) {

    }

    @Override
    public String toString() {
        return getUnloadType().getDesc() + ":areaId=" + getUnloadAreaId() + ",queuePoint=[" + (null == getQueuePoint() ? "" : getQueuePoint().toString()) + "],unloadPoints=" + (null == getUnloadPoints() ? "" : Arrays.toString(getUnloadPoints()));
    }
}
