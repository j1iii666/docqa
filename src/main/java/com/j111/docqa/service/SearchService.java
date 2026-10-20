package com.j111.docqa.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.j111.docqa.dto.SearchHit;
import com.j111.docqa.embedding.EmbeddingProvider;
import com.j111.docqa.embedding.VectorUtils;
import com.j111.docqa.entity.Document;
import com.j111.docqa.entity.DocumentChunk;
import com.j111.docqa.repository.DocumentChunkRepository;
import com.j111.docqa.repository.DocumentRepository;

/**
 * 向量检索。
 * <p>
 * 当前实现：把该用户的全部块加载到内存，逐条算点积（向量已归一化，点积即余弦相似度），取 topK。
 * <p>
 * 这个做法的天花板很明显 —— 块数到十万级就会 OOM 且慢。
 * 生产做法是用向量数据库（pgvector / Milvus / Qdrant）建 ANN 索引。
 * 学习项目先这样，能跑通并且能讲清"为什么以后要换"就够了。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int DEFAULT_TOP_K = 5;

    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final EmbeddingProvider embeddingProvider;

    public SearchService(DocumentChunkRepository chunkRepository,
                         DocumentRepository documentRepository,
                         EmbeddingProvider embeddingProvider) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.embeddingProvider = embeddingProvider;
    }

    @Transactional(readOnly = true)
    public List<SearchHit> search(Long userId, String query, Integer topK) {
        int k = topK == null ? DEFAULT_TOP_K : Math.min(Math.max(topK, 1), 20);

        float[] queryVector = embeddingProvider.embed(query);
        List<DocumentChunk> chunks = chunkRepository.findByUserId(userId);
        if (chunks.isEmpty()) {
            log.info("用户没有任何可检索的块: userId={}", userId);
            return List.of();
        }

        List<Scored> scored = chunks.stream()
                // 维度不一致说明是用旧模型生成的块，直接跳过（换模型后应重新向量化）
                .filter(c -> c.getEmbeddingDim() == queryVector.length)
                .map(c -> new Scored(c, VectorUtils.dot(queryVector, VectorUtils.deserialize(c.getEmbedding()))))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(k)
                .toList();

        if (scored.isEmpty()) {
            log.warn("所有块的向量维度都与当前模型不一致，需要重新向量化: userId={}", userId);
            return List.of();
        }

        Map<Long, Document> documents = documentRepository
                .findAllById(scored.stream().map(s -> s.chunk().getDocumentId()).toList())
                .stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));

        return scored.stream()
                .map(s -> {
                    Document doc = documents.get(s.chunk().getDocumentId());
                    return new SearchHit(
                            s.chunk().getId(),
                            s.chunk().getDocumentId(),
                            doc == null ? "(文档已删除)" : doc.getOriginalName(),
                            s.chunk().getChunkIndex(),
                            s.score(),
                            s.chunk().getContent());
                })
                .toList();
    }

    private record Scored(DocumentChunk chunk, float score) {
    }
}
