package com.baseboot.entry.dispatch.monitor.excavator;

import com.baseboot.entry.dispatch.monitor.LiveInfo;
import com.baseboot.entry.map.Point;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 电铲HMI信号
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExcavatorHmiInfo extends LiveInfo implements Serializable {

    private static final long serialVersionUID = -2692695204849609645L;

    /**
     * 电铲编号
     */
    private Integer excavatorId;

    /**
     * 生命信号
     */
    private int liveSign;

    /**
     * 命令字
     */
    private int commandNo;

    /**
     * 地图刷新:默认0，1代表刷新完成
     */
    private int mapRefreshFlag;
}
