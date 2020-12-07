package com.baseboot.entry.dispatch.monitor;

import lombok.Data;

@Data
public class LiveInfo {

    private long receiveTime = System.currentTimeMillis() - 10*1000;

    //是否连接
    private boolean linkFlag;

}
