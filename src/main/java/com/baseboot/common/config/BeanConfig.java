package com.baseboot.common.config;

import com.baseboot.common.service.DelayedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@Slf4j
@EnableAsync
public class BeanConfig {

    /**
     * 线程池 ThreadPoolTaskExecutor
     * */
    @Bean(name = "baseAsyncThreadPool")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor pool=new ThreadPoolTaskExecutor();
        int processors = Runtime.getRuntime().availableProcessors();
        pool.setCorePoolSize(processors);
        pool.setMaxPoolSize(processors*10);
        pool.setQueueCapacity(processors*20);
        pool.setKeepAliveSeconds(30);
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(60);
        pool.setThreadNamePrefix("base-async-thread");
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        pool.initialize();
        return pool;
    }

    /**
     * 异步任务中异常处理
     * */
    @Bean
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

    @Bean
    public DelayedService create(){
        return new DelayedService();
    }
}
