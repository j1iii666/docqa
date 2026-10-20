package com.j111.docqa.dto;

import java.time.LocalDateTime;

import com.j111.docqa.entity.Document;
import com.j111.docqa.entity.DocumentStatus;

/**
 * 文档信息（对外）。
 */
public record DocumentVO(
        Long id,
        String originalName,
        long fileSize,
        String contentType,
        DocumentStatus status,
        int textLength,
        int chunkCount,
        String errorMessage,
        LocalDateTime createdAt) {

    public static DocumentVO from(Document doc) {
        return new DocumentVO(
                doc.getId(),
                doc.getOriginalName(),
                doc.getFileSize(),
                doc.getContentType(),
                doc.getStatus(),
                doc.getTextLength(),
                doc.getChunkCount(),
                doc.getErrorMessage(),
                doc.getCreatedAt());
    }
}
