package com.example.travelagency.config;

import com.example.travelagency.booking.AsyncFailureHandler;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    private final AsyncFailureHandler failureHandler;

    public AsyncConfig(AsyncFailureHandler failureHandler) {
        this.failureHandler = failureHandler;
    }

    @Bean("travelExecutor")
    public ThreadPoolTaskExecutor travelExecutor(
            @Value("${travel.async.core-pool-size:2}") int corePoolSize,
            @Value("${travel.async.max-pool-size:2}") int maxPoolSize,
            @Value("${travel.async.queue-capacity:2}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("travel-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return failureHandler;
    }
}
