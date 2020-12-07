package com.baseboot.service.dispatch.task;

import com.baseboot.common.utils.BaseUtil;
import lombok.Data;

@Data
public class TriggerTask {

    private Task task;

    private String taskId;//任务id

    private long addTime;//添加时间

    private long timeOut;//过去时间

    private long delay = 0;//延时时间

    private TaskStateEnum taskState = TaskStateEnum.NONE;

    public TriggerTask(String taskId, long timeOut, Task task) {
        this.taskId = taskId;
        this.timeOut = timeOut;
        this.addTime = BaseUtil.getCurTime();
        this.task = task;
    }

    public TriggerTask(String taskId, long timeOut, long delay, Task task) {
        this(taskId, timeOut, task);
        this.delay = delay;
    }

    public TaskStateEnum execTask() {
        if (!TaskStateEnum.NONE.equals(taskState) && !TaskStateEnum.FAIL.equals(taskState)) {
            return this.getTaskState();
        }
        if (null != task && BaseUtil.getCurTime() - this.getAddTime() < this.getTimeOut()) {
            if (BaseUtil.getCurTime() - this.getAddTime() > delay) {
                boolean run = this.task.run();
                if (run) {
                    this.setTaskState(TaskStateEnum.SUCCESS);
                } else {
                    this.setTaskState(TaskStateEnum.FAIL);
                }
            }else{
                this.setTaskState(TaskStateEnum.FAIL);
            }
        } else {
            this.setTaskState(TaskStateEnum.EXPIRATION);
        }
        return this.getTaskState();
    }
}
