package com.baseboot.entry.global;

import com.baseboot.common.utils.BaseUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;

@Data
@Slf4j
public class BaseThread extends Thread {

    private String bindId;
    private FiFoQueue<Runnable> tasks = new FiFoQueue<>();
    private volatile boolean runFlag = false;
    private final Object lock = new Object();
    private int taskNumMax = 10;//任务阈值
    private volatile long preTaskTimeStamp = 0;//上个任务的时间错

    public BaseThread(String bindId) {
        this.bindId = bindId;
        this.setName("BT-" + bindId);
    }

    /**
     * 设置任务最大值，超过最大值时，清空所有任务,最好5-10
     */
    public void setMaxTask(int taskNumMax) {
        if (taskNumMax > 1) {
            this.taskNumMax = taskNumMax;
        }
    }

    /**
     * 添加任务
     */
    public void addTask(Runnable task, long timeStamp) {
        if (timeStamp < preTaskTimeStamp) {
            return;
        }
        preTaskTimeStamp = timeStamp;
        if (null != task) {
            tasks.put(task);
            synchronized (lock) {
                lock.notifyAll();
            }
        }
        if (!runFlag) {
            runFlag = true;
            this.start();
        }
    }

    /**
     * 取消线程
     */
    public void stopRun() {
        tasks.clear();
        runFlag = false;
    }

    @Override
    public void run() {
        while (runFlag) {
            try {
                Runnable task = tasks.get();
                if (null != task) {
                    long time = BaseUtil.getRunTime(task);
                    if (time > 100) {
                        log.warn("BT任务【{}】执行耗时：{}", bindId, time);
                    }
                } else {
                    synchronized (lock) {
                        lock.wait(1000);
                    }
                }
                checkTasks();
            } catch (Exception e) {
                log.error("【{}】线程执行异常,error={}", bindId, e);
            }
        }
    }

    private void checkTasks() {
        if (tasks.getSize() > taskNumMax) {
            log.warn("【{}】任务数大于阈值:{},count:{},清空任务!!!", bindId, taskNumMax, tasks.getSize());
            tasks.clear();
        }
    }
}
