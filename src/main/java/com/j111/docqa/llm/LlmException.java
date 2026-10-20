package com.j111.docqa.llm;

/**
 * 调用大模型失败时抛出，带上给用户看的原因。
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
