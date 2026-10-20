package com.j111.docqa.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 文档解析专用线程池。
 * <p>
 * 为什么要单独一个池：解析 PDF 是 CPU 密集 + 阻塞 IO 的活，
 * 如果和 Tomcat 的请求线程共用一个池，上传一多就把接口线程占满，整个服务卡死。
 * <p>
 * 参数怎么定（面试常问）：
 * <ul>
 *   <li>core 2 / max 4：本机 16G 内存、CPU 4~8 核，解析不是每秒几十个的量级，够用且省内存</li>
 *   <li>queue 50：超过就交给调用方线程执行（CallerRunsPolicy），宁可慢也不丢任务</li>
 *   <li>prestartAllCoreThreads：启动时就把核心线程建好，避免第一批请求变慢</li>
 * </ul>
 */
@Configuration
public class AsyncConfig {

    public static final String DOC_PARSE_EXECUTOR = "docParseExecutor";

    @Bean(name = DOC_PARSE_EXECUTOR)
    public ThreadPoolTaskExecutor docParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("doc-parse-");
        // 队列满了之后的策略：由提交任务的线程自己跑，保证任务不丢
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等服务关闭时把已排队的任务跑完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
