package com.j111.docqa.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.j111.docqa.embedding.EmbeddingProvider;
import com.j111.docqa.embedding.VectorUtils;
import com.j111.docqa.entity.DocumentChunk;
import com.j111.docqa.parser.TextChunker;
import com.j111.docqa.repository.DocumentChunkRepository;

/**
 * 把解析出的纯文本切成块并向量化入库。
 */
@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);

    private final DocumentChunkRepository chunkRepository;
    private final TextChunker textChunker;
    private final EmbeddingProvider embeddingProvider;

    public ChunkService(DocumentChunkRepository chunkRepository,
                        TextChunker textChunker,
                        EmbeddingProvider embeddingProvider) {
        this.chunkRepository = chunkRepository;
        this.textChunker = textChunker;
        this.embeddingProvider = embeddingProvider;
    }

    /**
     * 重新生成某文档的所有块（先删后插，保证幂等）。
     *
     * @return 生成的块数量
     */
    @Transactional
    public int rebuild(Long documentId, Long userId, String text) {
        chunkRepository.deleteByDocumentId(documentId);

        List<TextChunker.ChunkDraft> drafts = textChunker.split(text);
        if (drafts.isEmpty()) {
            log.warn("文档没有可切分的内容: documentId={}", documentId);
            return 0;
        }

        List<DocumentChunk> entities = new ArrayList<>(drafts.size());
        for (TextChunker.ChunkDraft draft : drafts) {
            float[] vector = embeddingProvider.embed(draft.content());

            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setUserId(userId);
            chunk.setChunkIndex(draft.index());
            chunk.setContent(draft.content());
            chunk.setStartOffset(draft.startOffset());
            chunk.setCharCount(draft.charCount());
            chunk.setEmbedding(VectorUtils.serialize(vector));
            chunk.setEmbeddingDim(vector.length);
            chunk.setEmbeddingModel(embeddingProvider.modelName());
            entities.add(chunk);
        }

        // 批量插入比逐条 save 少很多次往返
        chunkRepository.saveAll(entities);
        log.info("切分完成: documentId={}, 块数={}, 模型={}, 维度={}",
                documentId, entities.size(), embeddingProvider.modelName(), embeddingProvider.dimension());
        return entities.size();
    }
}
