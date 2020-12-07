package com.baseboot.common.utils;

import com.alibaba.fastjson.JSONObject;
import com.baseboot.common.service.DelayedService;
import com.baseboot.entry.global.IEnumDeserializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class BaseUtil {

    /**
     * 计算方法运行时间
     */
    public static long getRunTime(Runnable task) {
        if (null != task) {
            long bTime = getCurTime();
            task.run();
            return getCurTime() - bTime;
        }
        return 0;
    }

    /**
     * 现成休眠
     */
    public static void TSleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            log.error("线程休眠异常!");
        }
    }

    /**
     * 现成等待
     */
    public static void TWait(long time, Object obj) {
        try {
            synchronized (obj) {
                obj.wait(time);
            }
        } catch (InterruptedException e) {
            log.error("线程等待异常!");
        }
    }

    /**
     * 写入文件
     */
    public static void writeToFile(String fileName, String dataStr) {
        File file = new File(fileName);
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            fileWriter.write(dataStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 文件append数据
     */
    public static void writeAppendToFile(String fileName, String dataStr) {
        File file = new File(fileName);
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        try (FileWriter fileWriter = new FileWriter(fileName, true)) {
            fileWriter.write(dataStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取文件的最后一行
     */
    public static String getFileLastLine(String filePath) {
        try (RandomAccessFile rf = new RandomAccessFile(filePath, "r")) {
            long start = rf.getFilePointer();
            long last = rf.length() + start - 1;
            int c = -1;
            while (true) {
                rf.seek(last);
                c = rf.read();
                if (c == '\n' || c == '\r') {
                    String s = rf.readLine();
                    if (StringNotNull(s)) {
                        return s;
                    }
                }
                last--;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 延时定时器
     */
    public static void timer(Runnable task, long delay) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
                timer.cancel();
            }
        }, delay);
    }

    /**
     * 取消定时任务
     */
    public static void cancelDelayTask(String taskId) {
        DelayedService.Task task = DelayedService.getTask(taskId);
        if (null == task) {
            return;
        }
        log.debug("删除定时任务:{}", taskId);
        DelayedService.removeTask(taskId);
    }

    /**
     * ,模糊取消定时任务
     */
    public static void cancelDelayLikeTask(String likeTaskId) {
        log.debug("模糊删除定时任务:{}", likeTaskId);
        DelayedService.cancelDelayLikeTask(likeTaskId);
    }

    /**
     * 模糊判断是否存在定时任务
     */
    public static boolean isExistLikeTask(String likeTaskId) {
        return DelayedService.isExistLikeTask(likeTaskId);
    }

    /**
     * 立即执行任务
     */
    public static void execTaskAtOnce(String taskId) {
        DelayedService.execTaskAtOnce(taskId);
    }


    /**
     * 判断ip是否可用
     */
    public static boolean isAble(String ip) {
        boolean reachable = false;
        try {
            InetAddress address = InetAddress.getByName(ip);
            reachable = address.isReachable(500);
        } catch (IOException e) {
            log.error("判断ip是否可用异常", e);
        }
        return reachable;
    }

    /**
     * 获取当前时间，毫秒
     */
    public static long getCurTime() {
        return System.currentTimeMillis();
    }


    /**============================项目路径===============================*/
    /**
     * 获取项目根目录
     */
    public static String getAppPath() {
        return System.getProperty("user.dir") + File.separator;
    }

    /**
     * 获取资源目录路径
     */
    public static String getResourcePath() {
        URL url = Thread.currentThread().getContextClassLoader().getResource("");
        if (null != url) {
            return url.getPath();
        }
        return "";
    }

    /**
     * 判断是否是jar包中,true为jar
     */
    public static boolean isJar() {
        URL url = Thread.currentThread().getContextClassLoader().getResource("");
        if (null != url) {
            return url.getProtocol().equals("jar");
        }
        return false;
    }

    /**============================序列化===============================*/
    /**
     * 对象序列化
     */
    public static byte[] serializer(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream os = new ObjectOutputStream(bos)) {
            os.writeObject(obj);
            return bos.toByteArray();
        } catch (Exception e) {
            log.error("对象序列化失败", e);
        }
        return null;
    }

    /**
     * 对象反序列化
     */
    public static Object deserializer(byte[] bytes) {
        try (ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
             ObjectInputStream in = new ObjectInputStream(bin)) {
            return in.readObject();
        } catch (Exception e) {
            log.error("对象反序列化失败", e);
        }
        return null;
    }

    /**============================字符串操作===============================*/

    /**
     * 首字母小写
     */
    public static String toLowCaseFirst(String sourceStr) {
        if (StringNotNull(sourceStr)) {
            char[] chars = sourceStr.toCharArray();
            if (Character.isUpperCase(chars[0])) {
                chars[0] += 32;
                return String.valueOf(chars);
            }
        }
        return sourceStr;
    }

    /**
     * 截取匹配最后字符串的后一段
     */
    public static String subLastStr(String key, String match) {
        if (key.contains(match)) {
            return key.substring(key.lastIndexOf(match) + 1);
        }
        return "";
    }

    /**
     * 截取匹配最后字符串的前一段
     */
    public static String subIndexStr(String key, String match) {
        if (key.contains(match)) {
            return key.substring(0, key.lastIndexOf(match));
        }
        return "";
    }

    /**
     * 字符串替换
     */
    public static String replaceAll(String origin, String oldStr, String newStr) {
        return origin.replaceAll(oldStr, newStr);
    }


    /**============================数据类型转换===============================*/

    /**
     * {}匹配替换
     */
    public static String format(String format, Object... objs) {
        Pattern pattern = Pattern.compile("\\{([^}])*\\}");
        Matcher matcher = pattern.matcher(format);
        int index = 0;
        int num = 0;
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int start = matcher.start();
            sb.append(format.substring(index, start));
            if (null != objs && objs.length >= num + 1) {
                sb.append(objs[num]);
            }
            index = matcher.end();
            num++;
        }
        if (num == 0) {
            sb.append(format);
        }
        if (index < format.length()) {
            sb.append(format.substring(index));
        }
        return sb.toString();
    }

    /**
     * double类型保留小数位
     */
    public static double getDoubleScale(double dob, int scale) {
        return new BigDecimal(dob).setScale(scale, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    /**
     * 包装类型转换
     */
    public static <T> T typeTransform(Object obj, Class<T> clazz) {
        try {
            String s = String.valueOf(obj);
            Method method = clazz.getDeclaredMethod("valueOf", String.class);
            if (method != null) {
                return (T) method.invoke(null, s);
            }
            return null;
        } catch (Exception e) {
            log.error("类型转换失败", e);
            return null;
        }
    }


    /**
     * 基础类型和包装类型转换
     */
    public static Class typeClass(Class clazz) {
        Class re = null;
        if (null != clazz && clazz.isPrimitive()) {
            if (clazz.equals(int.class)) {
                re = Integer.class;
            } else if (clazz.equals(short.class)) {
                re = Short.class;
            } else if (clazz.equals(long.class)) {
                re = Long.class;
            } else if (clazz.equals(byte.class)) {
                re = Byte.class;
            } else if (clazz.equals(char.class)) {
                re = Character.class;
            } else if (clazz.equals(boolean.class)) {
                re = Boolean.class;
            } else if (clazz.equals(float.class)) {
                re = Float.class;
            } else if (clazz.equals(double.class)) {
                re = Double.class;
            }
        }
        return re;
    }

    /**
     * 转为字符串
     */
    public static String getString(Object obj) {
        if (null == obj) {
            return "";
        }
        return String.valueOf(obj);
    }

    /**============================集合操作===============================*/

    /**
     * 稀疏list集合元素
     */
    public static <T> List<T> sparseList(List<T> sourceList, int interval) {
        return IntStream.range(0, sourceList.size()).mapToObj(i -> {
            if (i % interval == 0) {
                return sourceList.get(i);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }


    /**
     * ============================map操作===============================
     */

    /**
     * 把JavaBean转化为map
     */
    public static Map<String, Object> bean2Map(Object bean) {
        try {
            Map<String, Object> map = new HashMap<>();
            BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass(), Object.class);
            PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
            for (PropertyDescriptor pd : pds) {
                String propertyName = pd.getName();
                Method m = pd.getReadMethod();
                Object properValue = m.invoke(bean);
                map.put(propertyName, properValue);
            }
            return map;
        } catch (Exception e) {
            log.error("map to bean error", e);
        }
        return null;
    }

    /**
     * 把Map转化为JavaBean
     */
    public static <T> T map2Bean(Map map, Class<T> clz) {
        try {
            T obj = clz.newInstance();
            BeanInfo b = Introspector.getBeanInfo(clz, Object.class);
            PropertyDescriptor[] pds = b.getPropertyDescriptors();
            for (PropertyDescriptor pd : pds) {
                Method setter = pd.getWriteMethod();
                setter.invoke(obj, map.get(pd.getName()));
            }
            return obj;
        } catch (Exception e) {
            log.error("map to bean error", e);
        }
        return null;
    }

    public static <T> T mapGet(Map map, String key, Class<T> tClass) {
        if (null == map || !map.containsKey(key)) {
            return null;
        }
        Object value = map.get(key);
        return tClass.cast(value);
    }

    /**
     * put并返回value
     */
    public static <K, T> T mapPutAndGet(Map<K, T> map, K key, T value) {
        if (map.containsKey(key)) {
            return map.get(key);
        } else {
            map.put(key, value);
            return value;
        }
    }

    /**
     * 对象属性转map，空属性、final踢出
     */
    public static Map<String, Object> obj2Map(Object obj) {
        Map<String, Object> map = new HashMap<>();
        if (obj != null) {
            Field[] fields = obj.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                String name = field.getName();
                try {
                    Object value = field.get(obj);
                    if (value != null) {
                        map.put(name, value);
                    }
                } catch (IllegalAccessException e) {
                    log.error("反射获取属性值失败", e);
                }
            }
        }
        return map;
    }

    /**
     * map键转为大写
     */
    public static void toLowerCase(Map<String, Object> map) {
        if (BaseUtil.mapNotNull(map)) {
            return;
        }
        Set<String> keys = new HashSet<>(map.keySet());
        keys.stream().forEach(each -> {
            Object value = map.get(each);
            map.remove(each);
            map.put(each.toUpperCase(), value);
        });
    }

    public static boolean mapContains(Map map, String... keys) {
        if (map != null && !map.isEmpty() && keys != null) {
            int len = 0;
            for (String key : keys) {
                if (map.containsKey(key)) {
                    len++;
                }
            }
            if (len == keys.length) {
                return true;
            }
        }
        return false;
    }

    /**
     * ============================json转换===============================
     */

    /**
     * 包含转换IEnum枚举
     */
    public static <T> T toObjIEnum(Object obj, Class<T> tClass) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
            SimpleModule simpleModule = new SimpleModule();
            simpleModule.addDeserializer(Enum.class, new IEnumDeserializer());
            mapper.registerModule(simpleModule);
            return mapper.readValue(String.valueOf(obj), tClass);
        } catch (IOException e) {
            log.error("jackson读取数据失败", e);
        }
        return null;
    }

    /**
     * 获取多级json对象
     */
    public static JSONObject getJsonObj(String json, String... keys) {
        if (null != json) {
            JSONObject object = JSONObject.parseObject(json);
            for (String key : keys) {
                if (object.containsKey(key)) {
                    object = object.getJSONObject(key);
                }
            }
            return object;
        }
        return null;
    }

    public static String toJson(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("jackson转换数据失败", e);
        }
        return "";
    }

    public static <T> T toObj(Object obj, Class<T> tClass) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
            return mapper.readValue(String.valueOf(obj), tClass);
        } catch (IOException e) {
            log.error("jackson读取数据失败", e);
        }
        return null;
    }

    //转换list，map
    public static <T> T toCollectObj(String json, Class collect, Class target) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JavaType jt = mapper.getTypeFactory().constructParametricType(collect, target);
            return mapper.readValue(json, jt);
        } catch (IOException e) {
            log.error("jackson读取数据失败", e);
        }
        return null;
    }


    /**================================图片转换====================================*/
    /**
     * 字符串转图片
     */
    public static boolean generateImage(String imgStr, String filePath) {
        if (imgStr == null) {
            return false;
        }
        BASE64Decoder decoder = new BASE64Decoder();
        try {
            // 解密
            byte[] b = decoder.decodeBuffer(imgStr);
            // 处理数据
            for (int i = 0; i < b.length; ++i) {
                if (b[i] < 0) {
                    b[i] += 256;
                }
            }
            File file = new File(filePath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            OutputStream out = new FileOutputStream(file);
            ;
            out.write(b);
            out.flush();
            out.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;

    }


    /**
     * 图片转字符串
     */
    public static String getImageStr(String filePath) {
        InputStream inputStream = null;
        byte[] data = null;
        try {
            inputStream = new FileInputStream(filePath);
            data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 加密
        BASE64Encoder encoder = new BASE64Encoder();
        return encoder.encode(data);
    }

    /**
     * ===========================数据判空===========================
     */
    public static boolean StringNotNull(String obj) {
        return null != obj && obj.length() > 0;
    }

    public static boolean objNotNull(Object obj) {
        return null != obj;
    }

    public static boolean mapNotNull(Map map) {
        return null != map && !map.isEmpty();
    }

    public static boolean CollectionNotNull(Collection collection) {
        return null != collection && collection.size() > 0;
    }

    public static boolean allObjNotNull(Object... objs) {
        for (Object obj : objs) {
            if (!objNotNull(obj)) {
                return false;
            }
        }
        return true;
    }

    public static boolean anyObjNotNull(Object... objs) {
        for (Object obj : objs) {
            if (objNotNull(obj)) {
                return true;
            }
        }
        return false;
    }

    public static boolean arrayNotNull(Object[] array) {
        return null != array && array.length > 0;
    }


}
