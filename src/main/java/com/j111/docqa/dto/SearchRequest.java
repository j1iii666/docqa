package com.j111.docqa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 检索请求。
 */
public record SearchRequest(

        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题最多 500 个字符")
        String query,

        /** 返回几条最相似的片段，不传默认 5，最大 20 */
        Integer topK) {
}
