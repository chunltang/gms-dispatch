package com.baseboot.service.dispatch.helpers;

public interface Helper<T extends HelpClazz> {

    /**
     * 获取辅助对象
     * */
    T getHelpClazz();

    void initHelpClazz(T t);
}
