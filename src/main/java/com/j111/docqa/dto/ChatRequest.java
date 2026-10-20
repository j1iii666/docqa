package com.j111.docqa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 问答请求。
 */
public record ChatRequest(

        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题最多 500 个字符")
        String query,

        /** 参与拼提示词的片段数，不传用配置里的默认值 */
        Integer topK) {
}
