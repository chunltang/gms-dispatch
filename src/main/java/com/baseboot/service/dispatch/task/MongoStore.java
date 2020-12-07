package com.baseboot.service.dispatch.task;

import com.baseboot.common.service.ForkJoinService;
import com.baseboot.common.service.MongoService;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * mongo存储
 */
public class MongoStore {

    /**
     * tableName,data
     */
    private final static Map<String, List<Object>> monitorStoreMap = new ConcurrentHashMap<>();

    /**
     * 添加数据到缓存
     */
    public static <T> void addToMongo(String tableName, T singleData) {
        monitorStoreMap.computeIfAbsent(tableName, (map) -> new CopyOnWriteArrayList<>()).add(singleData);
    }

    /**
     * 刷新数据到数据库
     */
    public static void mongoRefresh() {
        if (monitorStoreMap.isEmpty()) {
            return;
        }
        Map<String, List<Object>> dataMap = new HashMap<>(monitorStoreMap);
        monitorStoreMap.clear();
        dataMap.forEach(MongoService::insertManyExpire);
    }

    /*public static void main(String[] args) {
        System.setProperty("MONGO_SERVER_HOST", "10.22.22.106");
        Monitor monitor;
        for (int i = 0; i < 1000; i++) {
            monitor=new Monitor();
            MongoStore.addToMongo("monitor",monitor);
            BaseUtil.TSleep(2);
        }
        mongoRefresh();
    }*/

    /*public static void main(String[] args) {
        System.setProperty("MONGO_SERVER_HOST", "10.22.22.106");

        List<Monitor> monitors = MongoService.queryList("monitor_10001", new Document(), Monitor.class,0,0);
        System.out.println(monitors);
    }*/

    public static void main(String[] args) {
        System.setProperty("MONGO_SERVER_HOST", "192.168.43.96");
        /*List<Monitor> monitors = MongoService.queryListById("monitor_10006", Monitor.class, System.currentTimeMillis() - 70 * 1000, MongoService.LT);
        System.out.println(monitors.size());*/

        Monitor mobj;
        List<Monitor> monitors = new ArrayList<>();
        for (int i = 0; i < 10000000; i++) {
            mobj=new Monitor();
            String id = MongoService.getObjectId();
            mobj.set_id(id);
            monitors.add(mobj);
        }


        ForkJoinService<Monitor> joinService = new ForkJoinService<Monitor>(monitors, 0, monitors.size(), (dataList) -> {
            System.out.println(Thread.currentThread().getName() + ",size=" + dataList.size());
            MongoService.insertManyExpire("monitor_10002", dataList);
        });

        ForkJoinService.submit(joinService);
        ForkJoinService.closePool(1000 * 1000);
    }
}
