package com.baseboot.entry.global;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BasePool {

    private final static Map<String, BaseThread> pool = new ConcurrentHashMap<>();

    /**
     * 不存在时创建线程
     */
    public static BaseThread addIfAbsent(String bindId) {
        return pool.computeIfAbsent(bindId, (map) -> new BaseThread(bindId));
    }

    /**
     * 提交线程任务
     */
    public static void commit(long timeStamp, String bindId, Runnable task) {
        if (null != bindId && null != task) {
            addIfAbsent(bindId).addTask(task, timeStamp);
        }
    }

    /**
     * 结束线程任务
     */
    public static void cancel(String bindId) {
        if (pool.keySet().contains(bindId)) {
            BaseThread baseThread = pool.get(bindId);
            baseThread.stopRun();
            log.debug("基础线程池结束任务【{}】", bindId);
            pool.remove(bindId);
        }
    }
}
