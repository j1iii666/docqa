package com.j111.docqa.llm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 大模型客户端（DeepSeek / 任何 OpenAI 兼容接口）。
 * <p>
 * 用 JDK 自带的 HttpClient 而不是额外引入 WebClient：
 * 流式响应本质就是"读 HTTP 响应体里的 data: 行"，没必要为它再拖一套响应式框架进来。
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final String DONE = "[DONE]";

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 流式对话：每收到一个增量片段就回调一次 onDelta。
     * <p>
     * 这是阻塞方法（会一直读到流结束），调用方必须放在自己的线程池里跑，不能占 Tomcat 请求线程。
     */
    public void streamChat(String systemPrompt, String userPrompt, Consumer<String> onDelta) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LlmException("没有配置 DEEPSEEK_API_KEY 环境变量，无法调用大模型");
        }

        String body = buildRequestBody(systemPrompt, userPrompt);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String error = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("大模型返回非 200: status={}, body={}", response.statusCode(), error);
                throw new LlmException("大模型接口返回 " + response.statusCode() + "，请检查 API key 和余额");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue; // 空行、注释行（: keep-alive）等都跳过
                    }
                    String payload = line.substring("data:".length()).trim();
                    if (payload.isEmpty()) {
                        continue;
                    }
                    if (DONE.equals(payload)) {
                        break;
                    }
                    String delta = extractDelta(payload);
                    if (delta != null && !delta.isEmpty()) {
                        onDelta.accept(delta);
                    }
                }
            }
        } catch (LlmException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("大模型调用被中断", e);
        } catch (Exception e) {
            throw new LlmException("调用大模型失败: " + e.getMessage(), e);
        }
    }

    /** 从 SSE 的 data 负载里取出本次增量文本 */
    private String extractDelta(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            // 少数实现会推送无法解析的心跳，忽略即可，不能因此把整个回答中断
            log.debug("无法解析的流片段: {}", payload);
            return null;
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("stream", true);
        root.put("temperature", properties.getTemperature());

        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);

        return objectMapper.writeValueAsString(root);
    }
}
