package com.j111.docqa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.j111.docqa.entity.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /** 检索时把某个用户的全部块捞出来算相似度（学习项目规模够用） */
    List<DocumentChunk> findByUserId(Long userId);

    /** 重新解析时先清掉旧块，避免重复 */
    @Modifying
    @Transactional
    @Query("delete from DocumentChunk c where c.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);

    long countByDocumentId(Long documentId);

    long countByUserId(Long userId);
}
