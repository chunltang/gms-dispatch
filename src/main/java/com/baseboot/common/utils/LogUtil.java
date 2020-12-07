package com.baseboot.common.utils;


import com.baseboot.common.service.RedisService;
import com.baseboot.entry.global.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
public class LogUtil {

    private final static Map<String, Long> PRINT_INTERVAL_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, List<MarkLog>> FILE_LOG_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, List<MarkLog>> REDIS_LOG_CACHE = new ConcurrentHashMap<>();
    private final static long FILE_LOG_MAX_NUMS = 300 * 1000;
    private final static long REDIS_LOG_MAX_NUMS = 10 * 1000;

    /******************************** 日志打印节奏控制 *********************************/

    public static void printLog(Runnable task, String uniqueId, long interval) {
        Map<String, Long> printCache = PRINT_INTERVAL_CACHE;
        if (printCache.containsKey(uniqueId)) {
            if (BaseUtil.getCurTime() - printCache.get(uniqueId) > interval) {
                printCache.put(uniqueId, BaseUtil.getCurTime());
                task.run();
            }
        } else {
            printCache.put(uniqueId, BaseUtil.getCurTime());
            task.run();
        }
    }

    public static <T> void addLogToFile(LogType type, T groupId, String desc) {
        String key = String.valueOf(groupId);
        FILE_LOG_CACHE.computeIfAbsent(key, (map) -> Collections.synchronizedList(new ArrayList<>())).add(getMarkLog(type, key, desc));
    }

    private static MarkLog getMarkLog(LogType type, String groupId, String desc) {
        MarkLog markLog = new MarkLog();
        markLog.setDesc(desc);
        markLog.setType(type);
        markLog.setGroupId(groupId);
        markLog.setTime(DateUtil.formatStringFullTime());
        return markLog;
    }

    /**
     * 将日志刷新到文件
     */
    public static void fileLogRefresh() {

        if (FILE_LOG_CACHE.isEmpty()) {
            return;
        }
        //log.warn("将日志刷新到文件");
        Map<String, List<MarkLog>> logMap = new HashMap<>(FILE_LOG_CACHE);
        FILE_LOG_CACHE.clear();
        for (Map.Entry<String, List<MarkLog>> entry : logMap.entrySet()) {
            String key = entry.getKey();
            List<MarkLog> logList = entry.getValue();
            StringBuilder sb = new StringBuilder();
            for (MarkLog log : logList) {
                sb.append(log.toString()).append("\r\n");
            }
            String fileName = BaseUtil.getAppPath() + "command/" + key + ".txt";
            long lines = IOUtil.getFileLines(fileName);
            if (lines > FILE_LOG_MAX_NUMS * 2) {
                String retain = IOUtil.getFileBackLineString(fileName, FILE_LOG_MAX_NUMS);
                sb.insert(0, retain);
                IOUtil.writeToFile(fileName, sb.toString(), false);
                return;
            }
            IOUtil.writeToFile(fileName, sb.toString(), true);
        }
    }

    /**
     * 添加异常日志
     */
    public static <T> void addLogToRedis(LogType type, T groupId, String desc) {
        String key = String.valueOf(groupId);
        MarkLog markLog = getMarkLog(type, key, desc);
        RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.LOG_DISPATCH, BaseUtil.toJson(markLog));
        REDIS_LOG_CACHE.computeIfAbsent(key, (map) -> Collections.synchronizedList(new ArrayList<>())).add(markLog);
    }

    /**
     * 日志存到Redis
     */
    public static void redisLogRefresh() {
        if (REDIS_LOG_CACHE.isEmpty()) {
            return;
        }
        //log.warn("将日志刷新到Redis");
        Map<String, List<MarkLog>> logMap = new HashMap<>(REDIS_LOG_CACHE);
        REDIS_LOG_CACHE.clear();
        for (Map.Entry<String, List<MarkLog>> entry : logMap.entrySet()) {
            String key = RedisKeyPool.LOG_DISPATCH;
            Long size = RedisService.listSize(BaseConstant.KEEP_DB, key);
            while (size > REDIS_LOG_MAX_NUMS) {
                RedisService.listTrim(BaseConstant.KEEP_DB, key, size / 5, size);
                size = RedisService.listSize(BaseConstant.KEEP_DB, key);
            }
            List<MarkLog> logList = entry.getValue();
            List<String> stringList = logList.stream().map(BaseUtil::toJson).collect(Collectors.toList());
            RedisService.listAdd(BaseConstant.KEEP_DB, key, stringList);
        }
    }

    /**
     * 获取redis日志
     */
    public static <T> List<MarkLog> getRedisLog(T groupId) {
        String key = RedisKeyPool.LOG_DISPATCH;
        List<String> keys = RedisService.keys(BaseConstant.KEEP_DB, key);
        List<MarkLog> result = new ArrayList<>();
        if (BaseUtil.CollectionNotNull(keys)) {
            for (String s : keys) {
                List<String> strings = RedisService.getList(BaseConstant.KEEP_DB, s);
                if (!BaseUtil.CollectionNotNull(strings)) {
                    return result;
                }
                for (String str : strings) {
                    result.add(BaseUtil.toObj(str, MarkLog.class));
                }
            }
        }
        return result;
    }

    /**
     * 实时文件日志,前10分钟
     */
    public static void todayFileLogRefresh() {
        String logPath = BaseUtil.getAppPath() + "log";
        Calendar instance = Calendar.getInstance();
        instance.setTime(new Date());
        instance.add(Calendar.MINUTE, -15);
        String dateStr = DateUtil.getDateFormat(instance.getTime(), DateUtil.FULL_TIME_SPLIT_MILLIS_PATTERN);
        String dateFormat = DateUtil.getDateFormat(new Date(), "yyyy-MM-dd");
        List<File> fileList = new ArrayList<>();
        List<File> needList = new ArrayList<>();
        IOUtil.listFiles(new File(logPath), fileList);
        for (File file : fileList) {
            if (file.getName().contains(dateFormat)) {
                needList.add(file);
            }
        }

        List<String> lines = new ArrayList<>();
        for (File file : needList) {
            List<String> strings = IOUtil.getFileLineList(file);//2020-11-19 11:44:43.539
            strings.removeIf((line) -> line.length() < 23 || !line.substring(0, 23).matches("([0-9]{4}-[0-9]{2}-[0-9]{2}\\s[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3})") || dateStr.compareTo(line) > 0);
            lines.addAll(strings);
        }
        lines.sort(Comparator.comparing(o -> o.substring(0, 23)));
        RedisService.asyncSet(BaseConstant.MONITOR_DB, RedisKeyPool.LOG_FILE_DISPATCH, String.join("\\n", lines.toArray(new String[]{})));
    }


    /**
     * 定时任务，日志缓存
     */
    public static void logRefresh() {
        fileLogRefresh();
        redisLogRefresh();
        todayFileLogRefresh();
    }

    public static void main(String[] args) {
        System.setProperty("REDIS_SERVER_HOST", "192.168.43.96");
        addLogToRedis(LogType.WARN, "map", "qqqqqqqqqqqqqqq");
    }
}
