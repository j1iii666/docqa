package com.j111.docqa.dto;

/**
 * 一条检索命中。
 *
 * @param score 相似度，越大越相关（本地哈希向量下通常 0~1，真实 embedding 模型可能为负）
 */
public record SearchHit(
        Long chunkId,
        Long documentId,
        String documentName,
        int chunkIndex,
        float score,
        String content) {
}
