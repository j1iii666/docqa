package com.j111.docqa.service;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.j111.docqa.config.AsyncConfig;
import com.j111.docqa.entity.Document;
import com.j111.docqa.entity.DocumentStatus;
import com.j111.docqa.parser.TextExtractor;
import com.j111.docqa.repository.DocumentRepository;

/**
 * 异步解析：上传接口立刻返回，真正的解析在 doc-parse-* 线程里跑。
 * <p>
 * 为什么不在上传时同步解析：一个 10MB 的 PDF 解析要几秒，
 * 同步解析会让上传接口超时，用户以为传失败了又传一遍。
 */
@Service
public class DocumentParseService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseService.class);

    private final DocumentRepository documentRepository;
    private final TextExtractor textExtractor;
    private final FileStorageService fileStorageService;

    public DocumentParseService(DocumentRepository documentRepository,
                                TextExtractor textExtractor,
                                FileStorageService fileStorageService) {
        this.documentRepository = documentRepository;
        this.textExtractor = textExtractor;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 注意：@Async 方法必须从"另一个 bean"里调用才会走代理生效；
     * 在同一个类里自己调自己（this.parseAsync）是同步执行的，这是最经典的坑。
     */
    @Async(AsyncConfig.DOC_PARSE_EXECUTOR)
    public void parseAsync(Long documentId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.warn("文档不存在，跳过解析: id={}", documentId);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            doc.setStatus(DocumentStatus.PARSING);
            documentRepository.save(doc);

            Path file = Paths.get(doc.getStoredPath());
            String text = textExtractor.extract(file, doc.getOriginalName());

            Path textPath = fileStorageService.saveText(documentId, text);

            doc.setStatus(DocumentStatus.SUCCESS);
            doc.setTextPath(textPath.toString());
            doc.setTextLength(text.length());
            doc.setErrorMessage(null);
            documentRepository.save(doc);

            log.info("解析完成: id={}, name={}, 字符数={}, 耗时={}ms",
                    documentId, doc.getOriginalName(), text.length(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 单篇解析失败绝不能把线程池搞挂，这里兜住所有异常，把失败原因回写到表里
            log.error("解析失败: id={}", documentId, e);
            doc.setStatus(DocumentStatus.FAILED);
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            doc.setErrorMessage(message.length() > 480 ? message.substring(0, 480) : message);
            documentRepository.save(doc);
        }
    }
}
