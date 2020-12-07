package com.baseboot.entry.global;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Export {

    /**
     * 文件名称,不带后缀
     * */
    private String fileName;

    /**
     * sheet名称
     * */
    private String sheetName;

    /**
     * 数据表头,{cnName:chName}
     * */
    private Map<String,String> headers;

    /**
     * 数据
     * */
    private List<Map<String,Object>> exportData;

    private Type type;

    public void setFileName(String fileName){
        if(null==this.type){
            this.fileName=fileName;
        }else{
            this.fileName=fileName+"."+this.type.name();
        }
    }

    public void setType(Type type){
        this.type=type;
        if(null!=this.fileName){
            this.fileName=fileName+"."+this.type.name();
        }
    }

    public enum Type{
        xls,
        xlsx
    }
}
