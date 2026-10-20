package com.j111.docqa.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 文本块（chunk）：一份文档切出来的若干段，是检索的最小单位。
 * <p>
 * 为什么要有 chunk 表而不是每次查询时现切：
 * 向量化是有成本的（一次 API 调用或一次模型前向），必须算一次存起来复用。
 */
@Entity
@Table(name = "document_chunk", indexes = {
        @Index(name = "idx_chunk_user", columnList = "user_id"),
        @Index(name = "idx_chunk_doc", columnList = "document_id, chunk_index")
})
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 冗余存 userId：检索时按用户过滤，避免每次都 join 文档表 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 在原文中的序号，从 0 开始，用于把命中片段还原到文档里的位置 */
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    /** 块内容。必须显式指定 MEDIUMTEXT：@Lob 在 Hibernate 7 下会被映射成 tinytext（255 字节），装不下几百字 */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    /** 在原文中的起始字符位置 */
    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    /**
     * 向量，存成逗号分隔的 float 字符串。
     * <p>
     * 学习项目这样够用（MySQL 8 没有原生向量类型）；
     * 真实生产要换 pgvector / Milvus / Qdrant 这类专用向量库，否则检索要全表加载到内存。
     * <p>
     * 同样必须显式 MEDIUMTEXT：512 维 float 转字符串大约 5KB，tinytext 装不下。
     */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String embedding;

    /** 向量维度与模型名：将来换 embedding 模型时，靠这两个字段判断哪些块需要重新向量化 */
    @Column(name = "embedding_dim", nullable = false)
    private int embeddingDim;

    @Column(name = "embedding_model", nullable = false, length = 64)
    private String embeddingModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    public int getCharCount() {
        return charCount;
    }

    public void setCharCount(int charCount) {
        this.charCount = charCount;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public int getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(int embeddingDim) {
        this.embeddingDim = embeddingDim;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
