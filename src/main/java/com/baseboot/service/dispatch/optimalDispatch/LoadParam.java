package com.baseboot.service.dispatch.optimalDispatch;

import lombok.Data;

/**
 * 装载点参数
 * */
@Data
public class LoadParam {

    private Integer loadId;

    /**
     * 装载时间
     * */
    private int loadingTime;

    /**
     * 等装时间
     * */
    private int waitLoadTime;
}
