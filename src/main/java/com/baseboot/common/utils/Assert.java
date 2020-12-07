package com.baseboot.common.utils;

import com.baseboot.common.exception.BaseException;

public class Assert {

    /**
     * @param message 异常描述，使用objs替换{}
     * @param arr 需要判空的数组
     * @param objs 需要替换的内容
     * */
    public static void AllNotNull(String message,Object[] arr, Object... objs) {
        if (!BaseUtil.arrayNotNull(arr)) {
            throw new BaseException(BaseUtil.format(message, objs));
        }
    }

    public static void notNull(Object obj, String message, Object... objs) {
        if (!BaseUtil.objNotNull(obj)) {
            throw new BaseException(BaseUtil.format(message, objs));
        }
    }

    public static void hasLength(String str, String message, Object... objs) {
        if (!BaseUtil.StringNotNull(str)) {
            throw new BaseException(BaseUtil.format(message, objs));
        }
    }
}
