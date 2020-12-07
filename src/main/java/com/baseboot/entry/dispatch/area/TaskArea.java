package com.baseboot.entry.dispatch.area;

import com.baseboot.entry.global.AbstractEventPublisher;
import com.baseboot.entry.global.BaseRedisCache;
import com.baseboot.entry.global.EventPublisher;
import com.baseboot.enums.AreaTypeEnum;

public interface TaskArea extends BaseRedisCache {

     Integer getTaskAreaId();

     AreaTypeEnum getAreaType();

     @SuppressWarnings("unchecked")
     default <T>T getInstance(){
         Class<? extends TaskArea> aClass = getAreaType().getTaskAreaClass();
         return (T)aClass.cast(this);
     };
}
