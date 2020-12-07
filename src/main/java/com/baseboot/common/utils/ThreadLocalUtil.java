package com.baseboot.common.utils;

public class ThreadLocalUtil{

    private static final ThreadLocal<Object> local = new ThreadLocal<>();

    public static void set(Object obj) {
        local.set(obj);
    }

    public static Object get() {
        return local.get();
    }

    public static void remove() {
        local.remove();
    }
}
