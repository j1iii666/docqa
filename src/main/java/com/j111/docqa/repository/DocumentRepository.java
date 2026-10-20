package com.j111.docqa.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.j111.docqa.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** 分页查某个用户的文档，按创建时间倒序由 Pageable 里的 Sort 决定 */
    Page<Document> findByUserId(Long userId, Pageable pageable);

    /** 查询时带上 userId，防止 A 用户拿 B 用户的文档 id 越权访问 */
    Optional<Document> findByIdAndUserId(Long id, Long userId);
}
