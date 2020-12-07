package com.baseboot.entry.global;

import lombok.Data;

@Data
public class MarkLog {

    private String groupId;

    private String Desc;

    private String time;

    private LogType type;

    @Override
    public String toString() {
        return "[" + this.groupId + "] [" + this.time + "] :" + this.Desc;
    }
}
