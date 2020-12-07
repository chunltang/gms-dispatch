package com.baseboot.common.service;


import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.RedisKeyPool;
import com.baseboot.common.utils.BaseUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;

@Slf4j
public class DelayedService extends Thread {

    /**
     * key为下次任务执行时间戳，毫秒,所有对taskMap中list<Task>的遍历都要用迭代器，不然出现读写异常
     */
    private final ConcurrentSkipListMap<Long, List<Task>> taskMap = new ConcurrentSkipListMap();

    private static DelayedService service;

    private final static long DEFAULT_AWAIT = 600 * 1000;

    private final static String TASKID_DEFAULT_PREFIX = "delay_task_";

    private static boolean DEFAULT_LOG_PRINT = false;

    private static volatile boolean isAwait = false;//是添加任务唤醒true，还是延时任务唤醒

    @Resource(name = "baseAsyncThreadPool")
    private Executor pool;

    @PostConstruct
    public void init() {
        service = this;
        service.pool = pool;
        service.setName("delay-vehicleTask-thread");
        service.start();
    }


    /**
     * 修改延时任务是否打印日志
     */
    public static void changePrintFlag(boolean flag) {
        DEFAULT_LOG_PRINT = flag;
        for (List<Task> tasks : service.taskMap.values()) {
            Iterator<Task> iterator = tasks.iterator();
            while (iterator.hasNext()) {
                iterator.next().setPrintLog(flag);
            }
        }
    }

    /**
     * 生成任务id
     */
    private static String generateId() {
        return TASKID_DEFAULT_PREFIX + RedisService.generateId();
    }

    /**
     * 添加延时任务，延时时间使用函数计算自动产生delay，delay参数将不起作用
     *
     * @param isGenerateId 在没有id时是否自动生成id
     */
    public static Task addCalculateTask(Task task, boolean isGenerateId) {
        if (BaseUtil.StringNotNull(task.getTaskId())) {
            addTask(task, 0);
            return task;
        }
        if (isGenerateId) {
            task.setTaskId(generateId());
            addTask(task, 0);
            return task;
        }
        return null;
    }

    /**
     * 添加任务
     */
    public static Task addTask(Runnable task, long delay) {
        return addTask(buildTask(task), delay);
    }

    /**
     * 添加任务,taskId不存在时添加,存在时返回已存在的任务
     */
    public static Task addTaskNoExist(Runnable task, long delay, String taskId, boolean atOnce) {
        if (!checkTaskId(taskId)) {
            return addTask(new Task().withTaskId(taskId).withTask(task).withAtOnce(atOnce), delay);
        }
        return getTask(taskId);
    }

    /**
     * 添加覆盖前缀的任务
     */
    public static Task addCoverPrefixTask(Runnable task, long delay, String prefix) {
        DelayedService.cancelDelayLikeTask(prefix);
        return addTask(task, delay);
    }

    /**
     * 添加新任务，下次执行时间跟当前时间有关
     */
    public static Task addTask(Task task, long delay) {
        if (null == task || null == task.getTask() || (delay <= 0 && null == task.getCalculate())) {
            log.error("参数异常");
            return null;
        }

        if (DEFAULT_LOG_PRINT) {//设置为true都打印，设置为false，根据自身设置处理，不设置默认不打印
            task.withPrintLog(DEFAULT_LOG_PRINT);
        }
        service.initTask(task, delay);
        awakeTask();
        return task;
    }

    public static Task addTask(Task task) {
        return addTask(task, task.getDelay());
    }

    /**
     * 添加已初始化的任务,根据设置nextTime执行下次任务
     *
     * @param replace 任务号存在时是否替换，true为替换
     */
    public static void addInitializedTask(Task task, boolean replace) {//通过这个方法添加的任务，不能设置立即执行
        if (null == task) {
            log.error("任务为空对象");
            return;
        }
        if (task.getNextTime() <= 0 || (task.getNum() != 1 && task.getDelay() <= 0)) {
            log.error("参数异常:{}", task.toString());
            return;
        }

        if (checkTaskId(task.getTaskId())) {
            log.debug("任务号{}已存在,是否重新加载:{}", task.getTaskId(), replace);
            if (replace) {
                updateTask(task);
            }
            return;
        }
        if (null == task.getMethodTask() && null == task.getTask()) {
            log.error("添加已初始化延时任务失败,methodTask和task都为空");
            return;
        }
        if (null != task.getMethodTask()) {//设置methodTask会覆盖task
            task.withMethodTask(task.getMethodTask());
        }
        service.put(task.getNextTime(), task);
        awakeTask();
    }

    private void initTask(Task task, long delay) {//初始任务信息
        if (null != task.getCalculate()) {//如果设置了获取时间的函数，则指定delay不起作用
            delay = (Long) task.getCalculate().getResult();
        }
        long curTime = BaseUtil.getCurTime();
        long next = curTime + delay;
        if (task.getAddTime() <= 0) {
            task.setAddTime(curTime);
        }
        task.setDelay(delay);
        if (task.isAtOnce()) {
            task.setNextTime(curTime);
            service.put(curTime, task);
        } else {
            task.setNextTime(next);
            service.put(next, task);
        }

    }

    private void put(long key, Task task) {
        synchronized (taskMap) {
            taskMap.computeIfAbsent(key, list -> Collections.synchronizedList(new ArrayList<Task>())).add(task);
        }
    }

    private synchronized void putAll(long key, List<Task> tasks) {
        synchronized (taskMap) {
            taskMap.computeIfAbsent(key, list -> Collections.synchronizedList(new ArrayList<Task>())).addAll(tasks);
        }
    }

    /**
     * 分析任务号是否已添加,true存在
     */
    private static boolean checkTaskId(String taskId) {
        for (List<Task> tasks : service.taskMap.values()) {
            Iterator<Task> iterator = tasks.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getTaskId().equals(taskId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 唤醒线程
     */
    private static void awakeTask() {
        synchronized (service.taskMap) {
            isAwait = true;
            service.taskMap.notifyAll();
        }
    }

    /**
     * 更新已有任务，替换原有任务
     */
    public static void updateTask(Task task) {
        if (null == task) {
            log.error("任务为空对象");
            return;
        }
        Long key = 0L;
        for (Map.Entry<Long, List<Task>> entry : service.taskMap.entrySet()) {
            List<Task> value = entry.getValue();
            Iterator<Task> iterator = value.iterator();
            while (iterator.hasNext()) {
                Task next = iterator.next();
                if (next.getTaskId().equals(task.getTaskId())) {
                    key = next.getNextTime();
                    break;
                }
            }
            value.removeIf(t -> t.getTaskId().equals(task.getTaskId()));
        }

        if (service.taskMap.containsKey(key) && service.taskMap.get(key).isEmpty()) {
            service.removeTaskKey(key);
        }
        addInitializedTask(task, true);
    }

    /**
     * 获取任务信息
     */
    public static Task getTask(String taskId) {
        synchronized (service.taskMap) {
            for (List<Task> tasks : service.taskMap.values()) {
                Iterator<Task> iterator = tasks.iterator();
                while (iterator.hasNext()) {
                    Task task = iterator.next();
                    if (task.getTaskId().equals(taskId)) {
                        return task;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 模糊判断是否存在任务
     */
    public static boolean isExistLikeTask(String likeTaskId) {
        synchronized (service.taskMap) {
            for (List<Task> tasks : service.taskMap.values()) {
                Iterator<Task> iterator = tasks.iterator();
                while (iterator.hasNext()) {
                    Task task = iterator.next();
                    if (task.getTaskId().contains(likeTaskId) && task.isNeedExec()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 立即自行任务
     */
    public static void execTaskAtOnce(String taskId) {
        Task task = getTask(taskId);
        if (null != task) {
            task.setNextTime(BaseUtil.getCurTime());
            updateTask(task);
        }
    }

    /**
     * 模糊取消任务
     */
    public static void cancelDelayLikeTask(String likeTaskId) {
        synchronized (service.taskMap) {
            for (List<DelayedService.Task> tasks : service.taskMap.values()) {
                Iterator<DelayedService.Task> iterator = tasks.iterator();
                while (iterator.hasNext()) {
                    DelayedService.Task task = iterator.next();
                    if (task.getTaskId().contains(likeTaskId)) {
                        task.withExec(false);
                    }
                }
            }
        }
    }

    /**
     * 删除指定任务
     */
    public static void removeTask(String taskId) {
        synchronized (service.taskMap) {
            long key = 0;
            for (Map.Entry<Long, List<Task>> entry : service.taskMap.entrySet()) {
                List<Task> tasks = entry.getValue();
                tasks.removeIf(t -> t.getTaskId().equals(taskId));
                if (tasks.size() == 0) {
                    key = entry.getKey();
                }
            }
            if (key != 0) {
                service.taskMap.remove(key);
            }

        }
    }

    /**
     * 删除指定key任务
     */
    private void removeTaskKey(Long key) {
        taskMap.remove(key);
    }

    /**
     * 任务等待
     */
    private void waitTask(long delay) {
        synchronized (taskMap) {
            try {
                isAwait = false;
                taskMap.wait(delay);
            } catch (InterruptedException e) {
                log.error("延时线程执行失败", e);
            }
        }
    }

    private void execute(long key) {//执行任务检查，是否执行任务
        ArrayList<Task> newTasks;
        synchronized (service.taskMap) {
            List<Task> tasks = service.taskMap.get(key);
            removeTaskKey(key);
            if (!BaseUtil.CollectionNotNull(tasks)) {
                return;
            }
            newTasks = new ArrayList<>(tasks);
        }
        Iterator<Task> iterator = newTasks.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.isNeedExec() && (task.getNum() > 0 || task.getNum() == -1)) {
                if (null == task.getTask()) {
                    log.error("延时任务task为null:{}", task.toString());
                    continue;
                }
                pool.execute(() -> {
                    try {
                        task.getTask().run();
                        if (null != task.getResult()) {
                            log.debug("taskId={},延时任务执行结果:{}", task.getTaskId(), task.getResult());
                        }
                    } catch (Exception e) {
                        log.error("延时任务执行异常:{}", task.getDesc(), e);
                    }
                });
                task.update();
                if (task.printLog) {
                    log.debug("定时延时任务:thread={},message={},taskId={},delay={},num={},step={}",
                            Thread.currentThread().getName(), task.getDesc(), task.getTaskId(), task.getDelay(), task.getNum(), task.getStep());
                }
            }
        }
        updateKey(newTasks);
    }


    private void updateKey(List<Task> tasks) {//更新下次任务执行时间,删除原有任务，添加新任务
        if (!tasks.isEmpty()) {
            for (Task task : tasks) {
                if (task.isNeedKeep()) {
                    RedisService.asyncSet(BaseConstant.KEEP_DB, RedisKeyPool.DELAY_TASK_PREFIX + task.getTaskId(), BaseUtil.toJson(task));
                }
                if (!task.isNeedExec() || task.getNum() == 0) {
                    if (null != task.getAfterTask()) {//执行后置任务
                        task.getAfterTask().run();
                    }
                    continue;
                }
                put(task.getNextTime(), task);
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Map.Entry<Long, List<Task>> entry = taskMap.firstEntry();
                if (null != entry) {
                    Long key = entry.getKey();
                    long val = key - BaseUtil.getCurTime();
                    if (val <= 0) {//任务过期时无条件执行
                        execute(key);
                        continue;
                    }
                    waitTask(val);//等待最执行时间的任务
                    //如果不是外部添加任务唤醒的线程，则为第一个任务执行时间到了,如果是外部任务唤醒则重新获取第一个任务
                    if (!isAwait) {
                        execute(key);
                    }
                } else {
                    waitTask(DEFAULT_AWAIT);
                }
            } catch (Exception e) {
                log.error("延时任务管理线程执行异常", e);
            }
        }
    }

    public static Task buildTask(Runnable task) {
        return buildTask().withTask(task);
    }

    public synchronized static Task buildTask() {//创建好任务后,可以在添加任务前覆盖自动生成的taskId
        String id;
        do {
            id = generateId();
        } while (checkTaskId(id));
        return new Task().withTaskId(id);
    }


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Task {

        private String taskId;

        @JsonIgnore
        private Runnable task;//执行任务

        @JsonIgnore
        private Runnable afterTask;//执行次数完成后，执行该任务

        private MethodTask methodTask;//从数据库区数据，使用MethodTask生成Task

        private MethodTask calculate;//通过计算获取下次执行时间,如果设置了calculate，则指定delay不起作用

        @JsonIgnore
        private List<Entry<Integer, Object>> result;//执行结果

        private long delay;//延时时间,毫秒

        private long nextTime;//下次执行时间

        private long preTime;//前一次执行时间

        private long addTime;//任务添加时间

        private boolean needKeep = false;//是否保存参数

        @JsonIgnore
        private boolean atOnce = false;//是否立即运行,该参数要起作用，需将该参数在添加任务之前设置

        private int num = 1;//执行次数，默认一次，-1为无次数限制

        private int step = 0;//当前执行次数

        private String desc;//任务描述

        private boolean needExec = true;//执行标志

        private boolean repetition = false;//在下次执行时间和当前时间相差多个循环时，是否重复执行

        private boolean printLog = DEFAULT_LOG_PRINT;//是否需要打印日志

        public void update() {//跟新下次任务执行时间
            this.step++;
            if (this.num != -1) {
                this.num--;
            }
            updateNextTime();
        }

        private void updateNextTime() {
            long delay = this.delay;
            this.preTime = this.nextTime;
            if (null != calculate) {
                delay = (Long) calculate.getResult();
            }
            long time = this.preTime;
            do {
                this.nextTime = time + delay;
                time += delay;
            } while (!this.repetition && this.nextTime < BaseUtil.getCurTime());
        }


        public Task withTaskId(String taskId) {//设置任务号
            this.taskId = taskId;
            return this;
        }

        public Task withKeep(boolean needKeep) {//设置是否保存任务参数
            this.needKeep = needKeep;
            return this;
        }

        public Task withAtOnce(boolean atOnce) {//设置立即运行,只有第一次生成任务时有效
            this.atOnce = atOnce;
            return this;
        }

        public Task withExec(boolean needExec) {
            this.needExec = needExec;
            return this;
        }

        public Task withNum(int num) {//设置执行次数
            this.num = num;
            return this;
        }

        public Task withDelay(long delay) {//设置延时参数,只在创建任务时添加任务前起作用
            this.delay = delay;
            return this;
        }

        public Task withNextTime(long nextTime) {
            this.nextTime = nextTime;
            return this;
        }

        public Task withAddTime(long addTime) {
            this.addTime = addTime;
            return this;
        }

        public Task withMethodTask(MethodTask methodTask) {
            this.withTask(methodTask.createTask());
            this.methodTask = methodTask;
            return this;
        }

        public Task withCalculate(MethodTask calculate) {
            calculate.createTask();
            this.calculate = calculate;
            return this;
        }

        public Task withCalculate(String className, String methodName, Object[] params) {
            return withCalculate(createMethodTask(className, methodName, params));
        }


        public MethodTask createMethodTask(String className, String methodName, Object[] params) {
            MethodTask classTask = new MethodTask();
            classTask.setClassName(className);
            classTask.setMethodName(methodName);
            classTask.setParams(params);
            return classTask;
        }


        public Task withMethodTask(String className, String methodName, Object[] params) {
            return withMethodTask(createMethodTask(className, methodName, params));
        }

        public Task withTask(Runnable task) {//设置任务
            this.task = task;
            return this;
        }

        public Task withAfterTask(Runnable afterTask) {//设置后置任务
            this.afterTask = afterTask;
            return this;
        }

        public Task withDesc(String desc) {//设置描述
            this.desc = desc;
            return this;
        }

        public Task withPrintLog(boolean isPrintLog) {//设置是否打印日志
            this.printLog = isPrintLog;
            return this;
        }

        public Task withRepetition(boolean repetition) {//设置间隔多次循环时是否重复执行
            this.repetition = repetition;
            return this;
        }

        public void putResult(Object result) {//添加任务执行结果，在添加任务时需要将执行结果put
            Entry<Integer, Object> entry = new Entry<>();
            entry.put(this.step, result);
            this.result = BaseUtil.CollectionNotNull(this.result) ? this.result : new ArrayList<>();
            this.result.add(entry);
        }

        @Data
        private static class Entry<K, V> {

            private K key;

            private V value;

            public void put(K key, V value) {
                this.key = key;
                this.value = value;
            }
        }
    }

    /**************test**************/
    /*private static int step=0;

    @PostConstruct
    public void test() {
        DelayedService.Task vehicleTask = DelayedService.Task.buildTask();//有返回值的样例
        vehicleTask.withTask(() -> {
            int exec = exec();
            vehicleTask.putResult(exec);
        }).withNum(10000);
        DelayedService.addInitializedTask(vehicleTask,20);

        DelayedService.Task task1 = DelayedService.buildTask()
                .withPrintLog(true)
                .withMessage("测试123")
                .withDelay(1000)
                .withCalculate("com.zs.gms.com.baseboot.service.init.GmsStartedUpRunner", "time", new Object[]{})
                .withKeep(true)
                .withNum(100)
                .withMethodTask("com.zs.gms.com.baseboot.service.init.GmsStartedUpRunner","print",new Object[]{})
                .withAtOnce(true)
                .withTask(()->{
                    long currentTimeMillis = System.currentTimeMillis();
                });
        DelayedService.addCalculateTask(task1);
    }

    private int exec(){
        System.out.println(++step);
        return step;
    }*/
}
