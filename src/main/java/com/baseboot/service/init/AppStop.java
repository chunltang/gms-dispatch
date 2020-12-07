package com.baseboot.service.init;

import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.global.LogType;
import com.baseboot.service.dispatch.task.MongoStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AppStop implements ApplicationListener<ContextClosedEvent> {
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        LogUtil.addLogToRedis(LogType.INFO,"dispatch","服务停止");
        LogUtil.logRefresh();
        MongoStore.mongoRefresh();
        log.warn("服务停止!");
    }
}
