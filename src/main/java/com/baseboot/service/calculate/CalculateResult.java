package com.baseboot.service.calculate;

import lombok.Data;

@Data
public class CalculateResult {

    /**
     * 施加对象(原对象,即给目标对象施加限制的对象)
     * */
    private Integer sourceId;

    /**
     * 作用对象(目标对象)
     * */
    private Integer targetId;

    /**
     * 路径索引
     * */
    private int index=1000000;


    private long addTime;

    private CalculateTypeEnum type;

}
