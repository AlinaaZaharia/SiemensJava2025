package com.siemens.internship.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * I added this 'AsyncConfig' class to set up a custom thread pool (itemExecutor)
 * for @Async methods, so I don't overload the default task executor
 * Based on: https://medium.com/@AlexanderObregon/how-spring-boot-configures-custom-thread-pools-for-async-processing-2f05d6fb3e42
 */

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "itemExecutor")
    public Executor itemExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(30);
        executor.initialize();
        return executor;
    }
}