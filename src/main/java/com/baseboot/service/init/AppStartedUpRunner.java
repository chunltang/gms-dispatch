package com.baseboot.service.init;

import com.baseboot.common.service.DelayedService;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.global.LogType;
import com.baseboot.entry.global.MongoKeyPool;
import com.baseboot.service.dispatch.task.MongoStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class AppStartedUpRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LogUtil.addLogToRedis(LogType.INFO, "dispatch", "服務启动");
        initSystem();
    }

    private void initSystem() {
        InitMethod.checkService();
        DelayedService.addTask(InitMethod::clearCache, 50).withDesc("清理缓存");
        DelayedService.addTask(InitMethod::dispatchInit, 1000).withDesc("调度初始化");
        DelayedService.addTask(() -> InitMethod.mapInit(null, true), 1000).withDesc("地图任务区初始化");
        DelayedService.addTask(InitMethod::linkCheck, 1000).withDesc("车辆连接检测").withNum(-1);
        DelayedService.addTask(InitMethod::readVehTroubleFile, 3000).withDesc("解析车载故障文件");
        DelayedService.addTask(InitMethod::heartbeat, 5000).withDesc("添加心跳").withNum(-1);
        DelayedService.addTask(InitMethod::codeCompress, 5000).withDesc("执行代码压缩");
        DelayedService.addTask(InitMethod::getMonitorCache, 5000).withDesc("缓存数据测试");
        DelayedService.addTask(InitMethod::mongoTableIndex, 5000).withDesc("mongo设置表数据过期");
        DelayedService.addTask(LogUtil::logRefresh, 30 * 1000).withDesc("日志缓存").withNum(-1);
        DelayedService.addTaskNoExist(MongoStore::mongoRefresh, 60 * 1000, MongoKeyPool.MONGO_STORE_REFRESH,false).withDesc("mongo数据缓存刷新").withNum(-1);
    }
}
