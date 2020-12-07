package com.baseboot.entry.dispatch.monitor.vehicle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Obstacle implements Serializable {

    private Integer id;


    private double x;

    private double y;

    private double z;

    private double length;
    private double width;
    private double height;
    private double angle;

    private double xSpeed;
    private double ySpeed;
    private double zSpeed;

    private int gridNumber;
    private List<Grid> grids;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class   Grid{
        private double x;

        private double y;

        private double z;
    }
}
