package com.baseboot.entry.dispatch.monitor;

import com.baseboot.common.utils.CollisionDetectUtil;
import com.baseboot.common.utils.CollisionDetectUtil.Rectangle;
import com.baseboot.entry.map.Point;
import com.baseboot.entry.map.SemiStatic;
import com.baseboot.service.DispatchUtil;

/**
 * 系统中所有具有GPS位置的对象
 */
public interface Location {

    /**
     * 连接
     * */
    void connection();

    /**
     * 断开连接
     * */
    void disConnection();

    /**
     * 获取矩形轮廓
     * @param  collisionDistance 碰撞检测外扩距离
     * */
    Rectangle getOutline(double collisionDistance);

    /**
     * 当前位置
     */
    Point getCurLocation();

    /**
     * 跟随安全距离
     */
    double followSafeDistance();

    /**
     * 停止安全距离,前后
     */
    double stopSafePADistance();

    /**
     * 停止安全距离,左右
     */
    double stopSafeRLDistance();

    /**
     * 唯一标识
     */
    Integer getUniqueId();

    /**
     * 在那个区域
     */
    default SemiStatic getArea() {
        Point location = getCurLocation();
        if (null != location) {
            return  DispatchUtil.isInnerArea(location);
        }
        return null;
    }
}
