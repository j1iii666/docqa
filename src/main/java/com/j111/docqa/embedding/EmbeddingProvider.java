package com.j111.docqa.embedding;

/**
 * 向量化抽象。
 * <p>
 * 为什么要抽接口：本地开发先用不花钱的实现（{@link HashingEmbeddingProvider}）把链路跑通，
 * 后面换成真实 embedding API（阿里云百炼 / 智谱 / OpenAI 等）只换实现，业务代码不用动。
 * <p>
 * 换实现时注意：**不同模型的向量空间不通用**，换模型必须全量重新向量化，
 * 所以块表里存了 embeddingModel 和 embeddingDim 两个字段。
 */
public interface EmbeddingProvider {

    /** 模型标识，会写进块表 */
    String modelName();

    /** 向量维度 */
    int dimension();

    /** 把一段文本变成一个向量（内部做归一化，方便后面直接用点积算余弦相似度） */
    float[] embed(String text);
}
