package com.j111.docqa.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本地哈希向量（feature hashing），不调用任何外部 API、零成本。
 * <p>
 * 原理：把文本拆成"单字 + 双字组合"（中文用 bigram 效果比单字好很多），
 * 每个 token 哈希到固定维度的某一维上累加权重，最后 L2 归一化。
 * <p>
 * 它的语义能力远不如真正的 embedding 模型，但：
 * <ul>
 *   <li>能把"切分 → 向量化 → 存储 → 检索"整条链路跑通并自动化测试</li>
 *   <li>字面重合度高的问句能检索到正确片段，足以验证工程正确性</li>
 * </ul>
 * 接上真实模型后，只要把 {@code docqa.embedding.provider} 切到 http 实现即可。
 */
@Component
public class HashingEmbeddingProvider implements EmbeddingProvider {

    private static final String MODEL = "local-hashing-v1";

    private final int dimension;

    public HashingEmbeddingProvider(@Value("${docqa.embedding.dimension:512}") int dimension) {
        this.dimension = Math.max(dimension, 64);
    }

    @Override
    public String modelName() {
        return MODEL;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String normalized = text.toLowerCase().replaceAll("\\s+", " ").strip();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (isMeaningful(c)) {
                add(vector, String.valueOf(c), 0.5f);
            }
            if (i + 1 < normalized.length()) {
                char next = normalized.charAt(i + 1);
                if (isMeaningful(c) && isMeaningful(next)) {
                    add(vector, normalized.substring(i, i + 2), 1.0f);
                }
            }
        }

        // L2 归一化：这样余弦相似度就等价于点积，检索时省一次开方
        double norm = 0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        if (norm > 0) {
            float scale = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) {
                vector[i] *= scale;
            }
        }
        return vector;
    }

    private void add(float[] vector, String token, float weight) {
        int index = Math.floorMod(token.hashCode(), dimension);
        vector[index] += weight;
    }

    /** 只保留中英文、数字，标点和空白不参与计算 */
    private boolean isMeaningful(char c) {
        return Character.isLetterOrDigit(c);
    }
}
