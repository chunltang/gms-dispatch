package com.baseboot.entry.global;

import lombok.Data;


@Data
public class Version {

    private String serviceName;//服务名称
    private String serviceVersion;//服务版本号
    private String startTime;//启动时间
    private String updateTime;//更新时间
    private String updateDesc;//跟新说明
}
