package com.baseboot.entry.dispatch.area;

import com.baseboot.entry.global.AbstractEventPublisher;
import com.baseboot.enums.AreaTypeEnum;
import com.baseboot.enums.UnLoadAreaStateEnum;
import lombok.Data;

@Data
public abstract class UnloadArea extends AbstractEventPublisher implements TaskArea {

    private Integer unloadAreaId;

    private QueuePoint queuePoint;

    private UnloadPoint[] unloadPoints;

    private AreaTypeEnum unloadType;

    private UnLoadAreaStateEnum state = UnLoadAreaStateEnum.ON;

    @Override
    public void updateCache() {

    }
}
