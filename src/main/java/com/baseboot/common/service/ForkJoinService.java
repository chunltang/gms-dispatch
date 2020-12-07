package com.baseboot.common.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

public class ForkJoinService<T> extends RecursiveAction {

    public final static ForkJoinPool forkJoinPool = new ForkJoinPool();

    private int nums = 1000;

    private int start;

    private int end;

    private List<T> dataList;

    private Task<T> task;


    public ForkJoinService(List<T> dataList, int start, int end, Task<T> task) {
        this.dataList = dataList;
        this.start = start;
        this.end = end;
        this.task = task;
    }

    public ForkJoinService(List<T> dataList, int start, int end, Task<T> task,int nums) {
        this.dataList = dataList;
        this.start = start;
        this.end = end;
        this.task = task;
        this.nums = nums;
    }

    public void setNums(int nums) {
        this.nums = nums;
    }


    @Override
    protected void compute() {
        if (end - start < nums) {
            try {
                task.execTask(dataList.subList(start, end));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            int mid = (end + start) / 2;
            ForkJoinService<T> left = new ForkJoinService<>(dataList, start, mid, task,nums);
            ForkJoinService<T> right = new ForkJoinService<>(dataList, mid, end, task,nums);
            left.fork();
            right.fork();
        }
    }

    /**
     * 提交任务
     */
    public static <T> void submit(ForkJoinService<T> service) {
        // 提交可分解的PrintTask任务
        forkJoinPool.submit(service);
    }

    /**
     * 关闭线程池
     */
    public static void closePool(long timeStamp) {
        try {
            forkJoinPool.awaitTermination(timeStamp, TimeUnit.MILLISECONDS);
            forkJoinPool.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public interface Task<T> {

        public void execTask(List<T> dataList);
    }

    public static void main(String[] args) throws InterruptedException {

        List<Integer> list = new ArrayList<>();
        int sum1 = 0;
        for (int i = 0; i < 10000; i++) {
            list.add(i);
            sum1 += i;
        }
        List<Integer> result = new CopyOnWriteArrayList<>();
        ForkJoinService<Integer> service = new ForkJoinService<>(list, 0, list.size(), new Task<Integer>() {
            @Override
            public void execTask(List<Integer> dataList) {
                System.out.println(Thread.currentThread().getName() + ":" + dataList.size());
                result.addAll(dataList);
            }
        });
        ForkJoinService.submit(service);
    }
}
