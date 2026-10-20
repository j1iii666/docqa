package com.j111.docqa.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型配置，对应 application.yml 的 docqa.llm.*
 * <p>
 * apiKey 从环境变量 DEEPSEEK_API_KEY 读，**不要写进 yml**，否则会跟着 git 泄漏。
 */
@Component
@ConfigurationProperties(prefix = "docqa.llm")
public class LlmProperties {

    /** OpenAI 兼容的接口根地址，DeepSeek 是 https://api.deepseek.com */
    private String baseUrl = "https://api.deepseek.com";

    private String apiKey = "";

    private String model = "deepseek-chat";

    /** 采样温度：问答场景要稳定，取小值 */
    private double temperature = 0.3;

    /** 单次请求超时（秒） */
    private int timeoutSeconds = 120;

    /** 拼进提示词的片段数量上限 */
    private int maxContextChunks = 5;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxContextChunks() {
        return maxContextChunks;
    }

    public void setMaxContextChunks(int maxContextChunks) {
        this.maxContextChunks = maxContextChunks;
    }
}
