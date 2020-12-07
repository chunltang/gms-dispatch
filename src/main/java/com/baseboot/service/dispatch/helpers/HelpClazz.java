package com.baseboot.service.dispatch.helpers;

public interface HelpClazz<T extends Helper> {

    /**
     * 获取辅助类
     * */
    T getHelper();

    void initHelper();
}
