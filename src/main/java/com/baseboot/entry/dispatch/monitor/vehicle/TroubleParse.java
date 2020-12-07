package com.baseboot.entry.dispatch.monitor.vehicle;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class TroubleParse {

    private String id;//异常部位id

    private String desc;//异常部位

    private Map<String,String>  keyVal=new HashMap<>();//故障对应描述

}
