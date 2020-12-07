package com.baseboot.common.utils;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class LambdaUtil {

    public static <T> String getName(BFunction<T, ?> fn) {
        Method writeReplaceMethod;
        try {
            writeReplaceMethod = fn.getClass().getDeclaredMethod("writeReplace");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        writeReplaceMethod.setAccessible(true);
        SerializedLambda serializedLambda;
        try {
            serializedLambda = (SerializedLambda) writeReplaceMethod.invoke(fn);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        String methodName = serializedLambda.getImplMethodName();
        String fileName = "";
        if (methodName.startsWith("get")) {
            fileName = methodName.substring("get".length());
        } else if (methodName.startsWith("is")) {
            fileName = methodName.substring("is".length());
        } else {
            throw new RuntimeException("不存在方法【" + methodName + "】对应的字段");
        }
        return fileName.replaceFirst(fileName.charAt(0) + "", (fileName.charAt(0) + "").toLowerCase());
    }
}
