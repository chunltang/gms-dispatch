package com.baseboot.common.service;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.entry.global.RedisKeyPool;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.support.ConnectionPoolSupport;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class RedisService {

    private static String HOST;
    private final static int PORT = 6379;
    private final static int TIMEOUT = 10;
    private final static GenericObjectPool<StatefulRedisConnection<String, String>> POOL;
    private final static BlockingQueue<RedisSetEntry> ASYNC_SET_QUEUE = new LinkedBlockingQueue<>();

    static {
        HOST = System.getProperty("REDIS_SERVER_HOST");
        RedisClient client = getRedisClient();
        GenericObjectPoolConfig<StatefulRedisConnection<String, String>> config = new GenericObjectPoolConfig<>();
        POOL = ConnectionPoolSupport.createGenericObjectPool(client::connect, config);
        execQueueTask();
    }

    private static void execQueueTask() {
        new Thread(() -> {
            RedisSetEntry entry;
            while (true) {
                try {
                    entry = ASYNC_SET_QUEUE.take();
                    RedisService.set(entry.getDbIndex(), entry.getKey(), entry.getValue(), entry.getDelay());
                } catch (Exception e) {
                    log.error("redis任务阻塞队列异常", e);
                }
            }
        }).start();
    }

    public static RedisClient getRedisClient() {
        RedisURI redisURI = RedisURI.builder().withHost(HOST).withPort(PORT).withTimeout(Duration.ofMinutes(TIMEOUT)).build();
        return RedisClient.create(redisURI);
    }

    public static boolean exists(int dbIndex, String key) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.exists(key) != 0;
        } catch (Exception e) {
            log.error("redis exists error :", e);
        }
        return false;
    }

    public static boolean asyncSet(int dbIndex, String key, String value, long... delay) {
        RedisSetEntry entry = new RedisSetEntry();
        entry.setKey(key);
        entry.setDelay(delay);
        entry.setValue(value);
        entry.setDbIndex(dbIndex);
        return ASYNC_SET_QUEUE.offer(entry);
    }

    public static boolean set(int dbIndex, String key, String value, long... delay) {
        if (null != delay && delay.length > 0) {
            return set(dbIndex, key, value, delay[0]);
        }
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisAsyncCommands<String, String> asyncCommands = connection.async();
            asyncCommands.select(dbIndex);
            asyncCommands.set(key, value);
            return true;
        } catch (Exception e) {
            log.error("redis asyncSet error :", e);
        }
        return false;
    }

    /**
     * 增加延时键值,异步
     */
    private static boolean set(int dbIndex, String key, String value, long seconds) {
        String scriptStr = "if redis.call('set',KEYS[1],ARGV[1]) then" +
                " redis.call('pexpire',KEYS[1],ARGV[2])" +
                " return 1;" +
                " else " +
                " return 0;" +
                " end;";
        return execScript(dbIndex, scriptStr, key, new String[]{value, String.valueOf(seconds)});
    }

    /**
     * 执行脚本
     */
    private static boolean execScript(int dbIndex, String scriptStr, String key, String[] value) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisAsyncCommands<String, String> asyncCommands = connection.async();
            asyncCommands.select(dbIndex);
            asyncCommands.eval(scriptStr, ScriptOutputType.BOOLEAN, new String[]{key}, value);
            return true;
        } catch (Exception e) {
            log.error("redis script error :", e);
        }
        return false;
    }


    public static boolean hSet(int dbIndex, String key, String field, String value) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.hset(key, field, value);
        } catch (Exception e) {
            log.error("redis hSet error :", e);
        }
        return false;
    }

    public static boolean hmSet(int dbIndex, String key, Map<String, String> values) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.hmset(key, values).equals("OK");
        } catch (Exception e) {
            log.error("redis hmSet error :", e);
        }
        return false;
    }


    public static Map<String, String> getAllHash(int dbIndex, String key) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.hgetall(key);
        } catch (Exception e) {
            log.error("redis mapGet error :", e);
        }
        return null;
    }

    public static String get(int dbIndex, String key) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.get(key);
        } catch (Exception e) {
            log.error("redis mapGet error :", e);
        }
        return "";
    }

    public static List<String> keys(int dbIndex, String pattern) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.keys("*" + pattern + "*");
        } catch (Exception e) {
            log.error("redis keys error :", e);
        }
        return null;
    }

    public static boolean del(int dbIndex, String... key) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.del(key) != 0;
        } catch (Exception e) {
            log.error("redis del error :", e);
        }
        return false;
    }

    /**
     * 获过期时间
     */
    public static Long ttl(int dbIndex, String key) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.ttl(key);
        } catch (Exception e) {
            log.error("redis ttl error :", e);
        }
        return 0L;
    }

    /**
     * list添加数据
     */
    public static boolean listAdd(int dbIndex, String key, String... values) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.lpush(key, values) != 0L;
        } catch (Exception e) {
            log.error("redis list add error :", e);
        }
        return false;
    }

    public static boolean listAdd(int dbIndex, String key, Collection<String> values) {
        return listAdd(dbIndex, key, values.toArray(new String[]{}));
    }

    public static List<String> getList(int dbIndex, String key) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.lrange(key, 0L, -1L);
        } catch (Exception e) {
            log.error("redis mapGet list error :", e);
        }
        return null;
    }

    public static Long listSize(int dbIndex, String key) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            return redisCommands.llen(key);
        } catch (Exception e) {
            log.error("redis mapGet list size error :", e);
        }
        return 0L;
    }

    public static void listTrim(int dbIndex, String key, long keepStart, long keepEnd) {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(dbIndex);
            redisCommands.ltrim(key, keepStart, keepEnd);
        } catch (Exception e) {
            log.error("redis  list del error :", e);
        }
    }


    /**
     * 删除模糊匹配的键
     */
    public static void delPattern(int dbIndex, String pattern) {
        List<String> keys = keys(dbIndex, pattern);
        if (BaseUtil.CollectionNotNull(keys)) {
            del(dbIndex, keys.toArray(new String[]{}));
        }
    }


    public static synchronized Long generateId() {
        try (StatefulRedisConnection<String, String> connection = POOL.borrowObject()) {
            RedisCommands<String, String> redisCommands = connection.sync();
            redisCommands.select(0);
            return redisCommands.incr(RedisKeyPool.REDIS_INCR);
        } catch (Exception e) {
            log.error("redis incr error :", e);
        }
        return null;
    }

    @Data
    private static class RedisSetEntry {

        private String key;
        private String value;
        private long[] delay;
        private int dbIndex;
    }
}
