package com.baseboot.service.dispatch.optimalDispatch;

import lombok.Data;

/**
 * 卸载区参数
 * */
@Data
public class UnloadParam {

    private Integer unloadId;

    /**
     * 卸载时间
     * */
    private int unloadingTime;
}
