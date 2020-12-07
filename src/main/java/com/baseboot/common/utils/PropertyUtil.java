package com.baseboot.common.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baseboot.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
public class PropertyUtil {
    /**
     * 判断对象的所有属性都为空，不判断静态属性，exclued为排除字段
     *
     * @return false为有属性不为空
     */
    public static boolean isAllFieldNull(Object obj, String... exluedes) {
        boolean flag = true;
        if (obj != null) {
            List<String> exluedeList = Arrays.asList(exluedes);
            Field[] fields = obj.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || exluedeList.contains(field.getName()))
                    continue;
                field.setAccessible(true);
                Object value = null;
                try {
                    value = field.get(obj);
                } catch (Exception e) {
                    log.error("获取对象属性失败", e);
                }
                if (value != null) {
                    flag = false;
                    break;
                }
            }
        }
        return flag;
    }

    /**
     * 有字段为空,true为有字段为空
     */
    public static boolean isAnyFiledNull(Object obj) {
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                if (null == value) {
                    return true;
                }
            } catch (Exception e) {
                log.error("获取对象属性失败", e);
            }
        }
        return false;
    }

    /**
     * 按对象属性名称生层次转换json数据
     */
    public static <T> T jsonToBean(String jsonStr, Class target) {
        if (Map.class.isAssignableFrom(target)) {
            return (T) JSONObject.parseObject(jsonStr, Map.class);
        } else if (List.class.isAssignableFrom(target)) {
            return (T) JSONArray.parseObject(jsonStr, List.class);
        } else {
            JSONObject jsonObject = JSONObject.parseObject(jsonStr);
            Object t = null;
            try {
                t = target.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Field[] fields = target.getDeclaredFields();
            handleField(fields, t, jsonObject);
            return (T) t;
        }
    }

    /**
     * 字段设值
     */
    private static void handleField(Field[] fields, Object t, JSONObject jsonObject) {
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            Class<?> type = field.getType();
            Object value = containKey(jsonObject, field.getName());
            if (null != value) {
                field.setAccessible(true);
                try {
                    if (value instanceof JSONObject) {//map,obj
                        field.set(t, JSONObject.parseObject(String.valueOf(value), type));
                        continue;
                    }
                    if (value instanceof JSONArray) {//数组,list
                        field.set(t, JSONArray.parseObject(String.valueOf(value), type));
                        continue;
                    }
                    if (Date.class.isAssignableFrom(type)) {//date
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        Date date = dateFormat.parse(value.toString());
                        field.set(t, date);
                        continue;
                    }

                    if (type.isPrimitive()) { //基础数据类型
                        if (type.isAssignableFrom(float.class)) {
                            field.set(t, Float.parseFloat(value.toString()));
                        } else if (type.isAssignableFrom(int.class)) {
                            field.set(t, Integer.parseInt(value.toString()));
                        } else if (type.isAssignableFrom(boolean.class)) {
                            field.set(t, Boolean.parseBoolean(value.toString()));
                        } else if (type.isAssignableFrom(long.class)) {
                            field.set(t, Long.parseLong(value.toString()));
                        } else if (type.isAssignableFrom(short.class)) {
                            field.set(t, Short.parseShort(value.toString()));
                        } else if (type.isAssignableFrom(char.class)) {
                            field.set(t, value.toString().charAt(0));
                        } else if (type.isAssignableFrom(double.class)) {
                            field.set(t, Double.parseDouble(value.toString()));
                        } else if (type.isAssignableFrom(byte.class)) {
                            field.set(t, Byte.parseByte(value.toString()));
                        }
                        continue;
                    }

                    if (hasField(type, "TYPE")) {//包装类获取基础数据类型
                        if (Character.class.isAssignableFrom(type)) {
                            Method m = type.getDeclaredMethod("valueOf", Character.class);
                            Object invoke = m.invoke(null, value.toString().charAt(0));
                            field.set(t, invoke);
                        } else {
                            Method m = type.getDeclaredMethod("valueOf", String.class);
                            Object invoke = m.invoke(null, value.toString());
                            field.set(t, invoke);
                        }
                        continue;
                    }

                    if (type.isEnum()) {//枚举类型
                        Object[] enumConstants = type.getEnumConstants();
                        Method getValue = type.getMethod("getKey");//需要定义getValue方法
                        for (Object enumConstant : enumConstants) {
                            Object invoke = getValue.invoke(enumConstant);
                            if (null != invoke && String.valueOf(invoke).equals(value.toString())) {
                                field.set(t, enumConstant);
                            }
                        }
                        continue;
                    }

                    if (String.class.isAssignableFrom(type)) {
                        field.set(t, String.valueOf(value));
                        continue;
                    }
                } catch (Exception e) {
                    log.error("反射异常", e);
                    continue;
                }
            } else {//转换对象中的自定义对象属性
                if (excludeType(type)) {
                    continue;
                }
                Field[] declaredFields = type.getDeclaredFields();
                try {
                    Object o = type.newInstance();
                    field.setAccessible(true);
                    field.set(t, o);
                    handleField(declaredFields, o, jsonObject);
                } catch (Exception e) {
                    continue;
                }
            }
        }
    }

    /**
     * 排除非自定义类型
     */
    private static boolean excludeType(Class<?> type) {
        if (null == type.getClassLoader()) {//核心加载器为空则为自定义类型
            return true;
        }
        return false;
    }

    /**
     * TYPE,判断是否是包装类，只有包装类才有TYPE字段
     */
    private static boolean hasField(Class clazz, String name) {
        try {
            clazz.getField(name);
        } catch (NoSuchFieldException e) {
            return false;
        }
        return true;
    }


    /**
     * 判断json中是否有指定键
     */
    private static Object containKey(JSONObject jsonObject, String containKey) {
        if (jsonObject.containsKey(containKey)) {
            return jsonObject.get(containKey);
        }

        Set<Map.Entry<String, Object>> entries = jsonObject.entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            Object value = entry.getValue();
            if (value instanceof Map || value instanceof JSONObject) {
                return containKey((JSONObject) value, containKey);
            }
        }
        return null;
    }

    /**
     * 加载属性文件
     */
    public static Properties loadProperties(String path) {
        Properties properties = new Properties();
        try {
            InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            InputStreamReader in = new InputStreamReader(stream, Charset.forName("UTF-8"));
            properties.load(in);
        } catch (IOException e) {
            log.error("属性文件路径不存在!", e);
        }
        return properties;
    }

    /**
     * 属性文件转为实体
     */
    public static <T> T propertiesToObj(String path, Class<T> tClass) {
        Properties properties = loadProperties(path);
        try {
            T instance = tClass.newInstance();
            Field[] fields = tClass.getDeclaredFields();
            Map<String, Field> fieldMap = new HashMap<>();
            for (Field field : fields) {
                fieldMap.put(field.getName(), field);
            }
            for (String name : properties.stringPropertyNames()) {
                if (fieldMap.keySet().contains(name)) {
                    Field field = fieldMap.get(name);
                    field.setAccessible(true);
                    field.set(instance, field.getType().cast(properties.get(name)));
                }
            }
            return instance;
        } catch (Exception e) {
            log.error("属性文件转为实体失败!");
        }
        return null;
    }

    /**
     * 保存属性文件
     */
    public static void storeProperties(Properties properties, String path) {
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(getFile(path)), StandardCharsets.UTF_8)) {
            properties.store(out, "Global Config");
        } catch (IOException e) {
            log.error("属性文件写入异常!", e);
        }
    }

    /**
     * 创建资源文件
     */
    private static File getFile(String path) {
        URL url = Thread.currentThread().getContextClassLoader().getResource("");
        Assert.notNull(url, "路径为空!");
        log.debug("资源根路径:{}",url.getPath());
        File file = new File(url.getPath() + path);
        if (!file.exists()) {
            throw new BaseException("资源文件不存在!");
        }
        return file;
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

    public static void main(String[] args) {
        Properties properties = PropertyUtil.loadProperties("properties/data.properties");
        Enumeration<?> names = properties.propertyNames();
        while (names.hasMoreElements()) {
            System.out.println(names.nextElement());
        }
        properties.setProperty("name", "tcl");
        properties.setProperty("name1", "tcl");
        properties.setProperty("name2", "tcl");
        PropertyUtil.storeProperties(properties, "properties/data.properties");
    }
}
