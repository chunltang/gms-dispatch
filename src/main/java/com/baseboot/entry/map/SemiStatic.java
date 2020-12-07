package com.baseboot.entry.map;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.enums.AreaTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 半静态层数据
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemiStatic implements Serializable {

    /*private static final long serialVersionUID = 2293471977359400735L;

    private Integer id;

    private String name;

    private AreaTypeEnum areaType;

    private Integer taskType;

    private Float speed;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<IdPoint> border;

    @JsonProperty(value = "queue_point")
    private IdPoint queuePoint;

    @JsonProperty(value = "task_spots")
    private TaskSpot[] taskSpots;

    @JsonProperty(value = "taskType")
    public Integer getTaskType(){
        return this.taskType;
    }

    @JsonProperty(value = "task_type")
    public void setTaskType(Integer taskType){
        this.taskType=taskType;
    }


    @Data
    public static class TaskSpot implements Serializable{

        private Integer id;

        IdPoint[] points;
    }*/

    //tcl 新地图改
    private Integer id;

    private String name;

    private AreaTypeEnum type;

    @JsonProperty(value = "max_speed")
    private Float speed;

    @JsonProperty(value = "max_veh")
    private int maxVeh;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<IdPoint> border;

    @JsonProperty(value = "area_spot_list")
    private TaskSpot[] taskSpots;

    @JsonProperty(value = "arc_vector")
    private Arc[] arcs;


    //获取排队点
    public IdPoint getQueuePoint() {
        if (BaseUtil.arrayNotNull(taskSpots)) {
            for (TaskSpot taskSpot : taskSpots) {
                if (taskSpot.getType() == 0) {
                    //功能点类型:0、排队点；1、任务点;
                    return new IdPoint(taskSpot.getId(),taskSpot.getX(), taskSpot.getY(), taskSpot.getZ(), taskSpot.getYawAngle());
                }
            }
        }
        return null;
    }

    //获取功能点
    public List<TaskSpot> getFuncPoint() {
        List<TaskSpot> result = new ArrayList<>();
        if (BaseUtil.arrayNotNull(taskSpots)) {
            for (TaskSpot taskSpot : taskSpots) {
                if (taskSpot.getType() == 1) {
                    //功能点类型:0、排队点；1、任务点;
                    result.add(taskSpot);
                }
            }
        }
        return result;
    }

    //获取区域类型
    public AreaTypeEnum getAreaType() {
        return this.type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Arc implements Serializable {

        private static final long serialVersionUID = -8123129372793193083L;

        @JsonProperty(value = "arc_id")
        private int arcId;

        @JsonProperty(value = "left_or_right")
        private int direction;
    }


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskSpot implements Serializable {

        private static final long serialVersionUID = 3807634300453197838L;

        private long id;

        @JsonProperty(value = "area_id")
        private int areaId;

        private int type;

        private double x;
        private double y;
        private double z;

        @JsonProperty(value = "yaw_angle")
        private double yawAngle;
        private double length;
        private double width;

        public IdPoint getPoint() {
            return new IdPoint(this.id,this.x, this.y, this.z, this.yawAngle);
        }
    }
}
