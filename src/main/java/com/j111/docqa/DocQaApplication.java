package com.j111.docqa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 启动类。@EnableAsync 让 @Async 生效（文档解析丢到线程池里做）。
 */
@SpringBootApplication
@EnableAsync
public class DocQaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocQaApplication.class, args);
    }
}
