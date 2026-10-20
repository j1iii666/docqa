package com.j111.docqa.controller;

import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.j111.docqa.config.AsyncConfig;
import com.j111.docqa.dto.ChatRequest;
import com.j111.docqa.ratelimit.RateLimit;
import com.j111.docqa.security.CurrentUserHolder;
import com.j111.docqa.service.ChatService;

import jakarta.validation.Valid;

/**
 * 问答接口（SSE 流式）。
 * <p>
 * 用 SseEmitter 而不是 WebFlux：项目是 Spring MVC（Tomcat），
 * SseEmitter 是 MVC 里做流式返回的标准做法，不用为此引入整套响应式栈。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** 单次问答最长 2 分钟，超时后 SseEmitter 会自己结束 */
    private static final long TIMEOUT_MS = 120_000L;

    private final ChatService chatService;
    private final ThreadPoolTaskExecutor chatExecutor;

    public ChatController(ChatService chatService,
                          @Qualifier(AsyncConfig.CHAT_EXECUTOR) ThreadPoolTaskExecutor chatExecutor) {
        this.chatService = chatService;
        this.chatExecutor = chatExecutor;
    }

    /**
     * 每个用户每分钟最多 10 次问答。
     * 问答是最贵的接口（一次调用要花大模型的钱），必须限流；
     * 额度按"每分钟 10 次"给，够正常对话，又挡得住脚本刷。
     */
    @RateLimit(capacity = 10, perSeconds = 60)
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        // 必须在请求线程里取出 userId：进异步线程后 ThreadLocal 已经是空的
        Long userId = CurrentUserHolder.require();

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        chatExecutor.execute(() -> chatService.streamAnswer(userId, request.query(), request.topK(), emitter));
        return emitter;
    }
}
