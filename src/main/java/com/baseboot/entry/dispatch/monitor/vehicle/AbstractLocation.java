package com.baseboot.entry.dispatch.monitor.vehicle;

import com.baseboot.common.utils.LogUtil;
import com.baseboot.entry.dispatch.monitor.Location;
import com.baseboot.entry.dispatch.monitor.LocationType;
import com.baseboot.entry.global.LogType;
import com.baseboot.service.BaseCacheUtil;

public abstract class AbstractLocation implements Location {

    private LocationType type;

    public LocationType getType() {
        return type;
    }

    /**
     * 设置类型，是否能移动
     */
    public void setType(LocationType type) {
        this.type = type;
    }

    public AbstractLocation() {
        BaseCacheUtil.addLocation(this);
    }


    @Override
    public void connection() {
        LogUtil.addLogToRedis(LogType.WARN, "connection"+getUniqueId(), "【" + getUniqueId() + "】设备连接");
    }

    @Override
    public void disConnection() {
        LogUtil.addLogToRedis(LogType.WARN, "disConnection"+getUniqueId(), "【" + getUniqueId() + "】设备断开连接");
    }
}
