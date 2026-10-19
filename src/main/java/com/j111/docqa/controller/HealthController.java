package com.j111.docqa.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用于验证骨架是否跑通的最小接口。
 * 跑通后可以删掉，但先留着当"心跳"。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "docqa is up";
    }
}
