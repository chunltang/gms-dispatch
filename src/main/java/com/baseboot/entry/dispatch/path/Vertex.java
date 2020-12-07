package com.baseboot.entry.dispatch.path;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Data;

@Data
public class Vertex {

    private int no;//编号
    //8字节
    private double x;
    //8字节
    private double y;
    //8字节
    private double z;//24+16+40+1

    private int type;
    //横摆角
    private float direction;
    //坡度
    private float slope;
    //曲率
    private float curvature;
    //8字节
    private double leftDistance;
    //8字节
    private double rightDistance;
    //8字节
    private double maxSpeed;
    //8字节
    private double speed;
    //8字节
    //当前点与轨迹头的距离
    private double s;
    //一个字节
    //是否倒车
    private boolean reverse;

    public byte[] toByteArray(){
        ByteBuf buf = Unpooled.buffer(81);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeInt(this.type);
        buf.writeFloat(this.direction);
        buf.writeFloat(this.slope);
        buf.writeFloat(this.curvature);
        buf.writeDouble(this.leftDistance);
        buf.writeDouble(this.rightDistance);
        buf.writeDouble(this.maxSpeed);
        buf.writeDouble(this.speed);
        buf.writeDouble(this.s);
        buf.writeBoolean(this.reverse);
        return buf.array();
    }
}
