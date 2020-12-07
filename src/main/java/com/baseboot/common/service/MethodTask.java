package com.baseboot.common.service;


import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.SpringContextUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Data
@Slf4j
public class MethodTask {

    private String className;//全路径

    private String methodName;

    private Object[] params;

    @JsonIgnore
    private Runnable task;

    @JsonIgnore
    private Object result;

    public Runnable createTask() {//使用该方法会替换task参数
        if(null!=this.task){
            return this.task;
        }
        String methodName = this.methodName;
        String className = this.className;
        Object[] params = this.params;
        if (!(BaseUtil.StringNotNull(className) && BaseUtil.StringNotNull(methodName))) {
            log.error("参数异常");
        }
        ClassLoader loader = this.getClass().getClassLoader();
        try {
            Class<?> aClass = loader.loadClass(this.className);
            Method[] methods = aClass.getDeclaredMethods();
            List<Method> methodSet = new ArrayList<>();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    Class<?>[] types = method.getParameterTypes();
                    if (types.length != params.length) {
                        continue;
                    }
                    for (int i = 0; i < types.length; i++) {
                        Class typeClass = types[i];
                        if (types[i].isPrimitive()) {//基本类型
                            typeClass = BaseUtil.typeClass(types[i]);
                        }
                        if (!typeClass.isAssignableFrom(params[i].getClass())) {//没有做泛型最佳匹配
                            break;
                        }
                    }
                    methodSet.add(method);
                }
            }
            Method target = sureMethod(methodSet, params);
            if (null != target) {
                Runnable task;
                final Method method = target;
                if (Modifier.isStatic(target.getModifiers())) {
                    task = () -> {
                        try {
                            result = method.invoke(null, params);
                        } catch (Exception e) {
                            log.error("反射方法调用失败，method:{},args:{}", methodName, params,e);
                        }
                    };
                } else {
                    Object bean = SpringContextUtil.getBean(aClass);//添加的任务为spring管理
                    if (bean == null) {
                        bean = aClass.newInstance();
                    }
                    final Object obj = bean;
                    task = () -> {
                        try {
                            result = method.invoke(obj, params);
                        } catch (Exception e) {
                            log.error("反射方法调用失败，method:{},args:{}", methodName, params,e);
                        }
                    };
                }
                 this.task=task;
            }
        } catch (Exception e) {
            log.error("根据classTask反射异常", e);
        }
        return this.task;
    }

    /**
     * 获取返回值
     */
    public Object getResult() {
        createTask().run();
        return this.result;
    }

    /**
     * 找出继承参数最接近的方法
     */
    private Method sureMethod(List<Method> methods, Object[] params) {
        Method target = null;
        if (methods.size() == 1) {
            target = methods.get(0);
        }
        if (methods.size() > 1) {
            target = methods.get(0);
            Method nextMethod;
            for (int i = 0; i < methods.size() - 1; i++) {
                nextMethod = methods.get(i + 1);
                target = sureParam(target, nextMethod, params);
            }
        }
        return target;
    }

    private Method sureParam(Method curMethod, Method nextMethod, Object[] params) {
        Method target = curMethod;
        Class<?>[] types1 = curMethod.getParameterTypes();
        Class<?>[] types2 = nextMethod.getParameterTypes();
        int len = types1.length;
        for (int i = 0; i < len; i++) {
            Class curClass = types1[i];
            Class nextClass = types2[i];
            Class paramClass = params[i].getClass();
            if (curClass.isPrimitive()) {//基本类型不比较
                continue;
            }
            if (curClass.getName().equals(nextClass.getName())) {//同类型不用比较
                continue;
            }
            if (curClass.getName().equals(paramClass.getName())) {
                target = curMethod;
                continue;
            }
            if (nextClass.getName().equals(paramClass.getName())) {
                target = nextMethod;
                continue;
            }
            Class[] types = {curClass, nextClass, paramClass};
            Arrays.sort(types, new Comparator<Class>() {
                @Override
                public int compare(Class o1, Class o2) {
                    if (o1.isAssignableFrom(o2)) {
                        return 1;
                    }
                    return -1;
                }
            });
            if (types[0].equals(paramClass)) {
                if (curClass.equals(types[1])) {
                    target = curMethod;
                } else {
                    target = nextMethod;
                }
            } else if (types[1].equals(paramClass)) {
                if (curClass.equals(types[2])) {
                    target = curMethod;
                } else {
                    target = nextMethod;
                }
            } else if (types[2].equals(paramClass)) {
                if (curClass.equals(types[1])) {
                    target = curMethod;
                } else {
                    target = nextMethod;
                }
            }
            break;
        }
        return target;
    }
}
