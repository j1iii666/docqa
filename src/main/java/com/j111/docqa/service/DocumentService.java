package com.j111.docqa.service;

import java.nio.file.Path;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.j111.docqa.cache.AnswerCacheService;
import com.j111.docqa.common.BizException;
import com.j111.docqa.common.ResultCode;
import com.j111.docqa.dto.DocumentVO;
import com.j111.docqa.dto.PageResult;
import com.j111.docqa.entity.Document;
import com.j111.docqa.entity.DocumentStatus;
import com.j111.docqa.repository.DocumentRepository;

/**
 * 文档的上传、列表、详情。
 */
@Service
public class DocumentService {

    private static final int MAX_PAGE_SIZE = 50;

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentParseService documentParseService;
    private final AnswerCacheService answerCacheService;

    public DocumentService(DocumentRepository documentRepository,
                           FileStorageService fileStorageService,
                           DocumentParseService documentParseService,
                           AnswerCacheService answerCacheService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.documentParseService = documentParseService;
        this.answerCacheService = answerCacheService;
    }

    /**
     * 上传：落盘 → 写库（PENDING）→ 丢给线程池解析 → 立刻返回。
     * <p>
     * 这里故意**不加** @Transactional：解析线程是另一个连接，
     * 如果上传方法还开着事务，那条 PENDING 记录对它是不可见的（隔离级别下未提交不可见），
     * 解析线程会"查不到文档"直接跳过，文档就永远停在 PENDING。
     * 每条 repository 调用本身是独立事务，save() 返回时已经提交，所以去掉外层事务反而是对的。
     */
    public DocumentVO upload(Long userId, MultipartFile file) {
        Path stored = fileStorageService.save(file);

        Document doc = new Document();
        doc.setUserId(userId);
        doc.setOriginalName(file.getOriginalFilename());
        doc.setStoredPath(stored.toString());
        doc.setFileSize(file.getSize());
        doc.setContentType(file.getContentType());
        doc.setStatus(DocumentStatus.PENDING);
        doc = documentRepository.save(doc);

        documentParseService.parseAsync(doc.getId());

        // 新增文档后，该用户之前基于旧文档集算出的问答缓存全部作废
        // （否则会命中缓存，答案里缺少新上传的那份文档的内容）
        answerCacheService.evictByUser(userId);

        return DocumentVO.from(doc);
    }

    @Transactional(readOnly = true)
    public PageResult<DocumentVO> list(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Document> result = documentRepository.findByUserId(userId, pageable);
        return PageResult.of(result, DocumentVO::from);
    }

    @Transactional(readOnly = true)
    public DocumentVO detail(Long userId, Long documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .map(DocumentVO::from)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "文档不存在或无权访问"));
    }

    /** 读解析出的纯文本（预览用），只返回前 maxChars 个字符 */
    @Transactional(readOnly = true)
    public String previewText(Long userId, Long documentId, int maxChars) {
        Document doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "文档不存在或无权访问"));
        if (doc.getStatus() != DocumentStatus.SUCCESS) {
            throw new BizException(ResultCode.DOC_PARSE_FAILED, "文档还没解析成功，当前状态: " + doc.getStatus());
        }
        String text = fileStorageService.readText(doc.getTextPath());
        if (text == null) {
            throw new BizException(ResultCode.NOT_FOUND, "解析文本文件已丢失");
        }
        int limit = Math.min(Math.max(maxChars, 1), 5000);
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
