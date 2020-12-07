package com.baseboot.common.utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 矩形碰撞检测
 */
@Slf4j
public class CollisionDetectUtil {

    /**
     * 不带角度
     */
    public static Boolean collisionDetect(Rectangle ra, Rectangle rb) {
        if (ra.getCenterX() + ra.getHalfWidth() < rb.getCenterX() - rb.getHalfWidth() ||
                ra.getCenterY() + ra.getHalfLength() < rb.getCenterY() - rb.getHalfLength() ||
                rb.getCenterX() + rb.getHalfWidth() < ra.getCenterX() - ra.getHalfWidth() ||
                rb.getCenterY() + rb.getHalfLength() < ra.getCenterY() - ra.getHalfLength())
            return false;
        else
            return true;
    }

    /**
     * 带旋转角度,true为碰撞
     */
    public static Boolean collisionDetectByAngle(Rectangle ra, Rectangle rb) {
        if (null == ra || null == rb) {
            log.error("碰撞检测参数异常");
            return false;
        }

        double centerDistanceX = ra.getCenterX() - rb.getCenterX();
        double centerDistanceY = ra.getCenterY() - rb.getCenterY();

        // 投影到A的两根轴上
        for (int i = 0; i < ra.getAxis().length / 2; i++) {
            if (ra.getHalfLength() * Math.abs(ra.getAxis()[2 * i] * ra.getAxis()[0] + ra.getAxis()[1 + 2 * i] * ra.getAxis()[1]) +
                    ra.getHalfWidth() * Math.abs(ra.getAxis()[2 * i] * ra.getAxis()[2] + ra.getAxis()[1 + 2 * i] * ra.getAxis()[3]) +
                    rb.getHalfLength() * Math.abs(ra.getAxis()[2 * i] * rb.getAxis()[0] + ra.getAxis()[1 + 2 * i] * rb.getAxis()[1]) +
                    rb.getHalfWidth() * Math.abs(ra.getAxis()[2 * i] * rb.getAxis()[2] + ra.getAxis()[1 + 2 * i] * rb.getAxis()[3])
                    <= Math.abs(centerDistanceX * ra.getAxis()[2 * i] + centerDistanceY * ra.getAxis()[1 + 2 * i])) {
                return false;
            }
        }

        // 投影到B的两根轴上
        for (int i = 0; i < rb.getAxis().length / 2; i++) {
            if (rb.getHalfLength() * Math.abs(rb.getAxis()[2 * i] * rb.getAxis()[0] + rb.getAxis()[1 + 2 * i] * rb.getAxis()[1]) +
                    rb.getHalfWidth() * Math.abs(rb.getAxis()[2 * i] * rb.getAxis()[2] + rb.getAxis()[1 + 2 * i] * rb.getAxis()[3]) +
                    ra.getHalfLength() * Math.abs(rb.getAxis()[2 * i] * ra.getAxis()[0] + rb.getAxis()[1 + 2 * i] * ra.getAxis()[1]) +
                    ra.getHalfWidth() * Math.abs(rb.getAxis()[2 * i] * ra.getAxis()[2] + rb.getAxis()[1 + 2 * i] * ra.getAxis()[3])
                    <= Math.abs(centerDistanceX * rb.getAxis()[2 * i] + centerDistanceY * rb.getAxis()[1 + 2 * i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 矩形
     */
    @Data
    public static class Rectangle {

        private double centerX;//中心x
        private double centerY;//中心y

        private double halfLength;//一半高度
        private double halfWidth;//一半宽度

        private double angle;//角度

        private double[] axis = new double[4];

        public Rectangle(double centerX, double centerY, double length, double width, double angle) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.halfLength = length / 2;
            this.halfWidth = width / 2;
            this.angle = angle;
            setAxisByAngle(angle);
        }

        public void setAxisByAngle(double rotation) {
            this.axis[0] = (float) (Math.cos(Math.toRadians(rotation)));
            this.axis[1] = (float) (Math.sin(Math.toRadians(rotation)));
            this.axis[2] = -(float) (Math.sin(Math.toRadians(rotation)));
            this.axis[3] = (float) (Math.cos(Math.toRadians(rotation)));
        }

        public void setAngle(double angle) {
            this.angle = angle;
            setAxisByAngle(angle);
        }
    }
}
