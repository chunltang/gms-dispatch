package com.baseboot.entry.map;

import lombok.Data;

@Data
public class OriginPoint {

    private double x;

    private double y;

    private double z;

    @Override
    public String toString(){
        return "["+this.x+","+this.y+","+this.z+"]";
    }
}
