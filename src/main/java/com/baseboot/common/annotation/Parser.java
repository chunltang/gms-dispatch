package com.baseboot.common.annotation;

import com.baseboot.common.utils.BaseUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * 注解解析
 */
@Slf4j
public class Parser {

    /**
     * true验证通过
     */
    public static boolean notNull(Object target) {
        if(null==target){
            return false;
        }
        boolean result = true;
        Class<?> aClass = target.getClass();
        Field[] fields = aClass.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            NotNull annotation = field.getAnnotation(NotNull.class);
            if (null != annotation) {
                Object value = null;
                try {
                    value = field.get(target);
                } catch (IllegalAccessException e) {
                    log.error("字段解析失败", e);
                }
                if (!BaseUtil.objNotNull(value)) {
                    result = false;
                }
                if (value instanceof String) {
                    if (!BaseUtil.StringNotNull(String.valueOf(value))) {
                        result = false;
                    }
                } else if (value instanceof Object[]) {
                    if (!BaseUtil.arrayNotNull((Object[]) value)) {
                        result = false;
                    }
                } else if (value instanceof Collection) {
                    if (!BaseUtil.CollectionNotNull((Collection) value)) {
                        result = false;
                    }
                } else if (value instanceof Map) {
                    if (!BaseUtil.mapNotNull((Map) value)) {
                        result = false;
                    }
                }
                if (!result) {
                    log.debug("数据判空校验失败,Field:{}，Desc:{}", field.getName(), annotation.message());
                    return false;
                }
            }
        }
        return true;
    }
}
