package com.j111.docqa.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.j111.docqa.common.Result;
import com.j111.docqa.dto.DocumentVO;
import com.j111.docqa.dto.PageResult;
import com.j111.docqa.ratelimit.RateLimit;
import com.j111.docqa.security.CurrentUserHolder;
import com.j111.docqa.service.DocumentService;

/**
 * 文档接口，全部需要登录（AuthInterceptor 拦 /api/doc/**）。
 */
@RestController
@RequestMapping("/api/doc")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传。multipart/form-data，字段名必须叫 file。
     * 注意：这个接口此刻返回的 status 一定是 PENDING，解析在后台线程里跑。
     * <p>
     * 上传会影响线程池和磁盘，按每分钟 20 次限流。
     */
    @RateLimit(capacity = 20, perSeconds = 60)
    @PostMapping("/upload")
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.upload(CurrentUserHolder.require(), file));
    }

    /** 分页列表，page 从 1 开始 */
    @GetMapping("/list")
    public Result<PageResult<DocumentVO>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.ok(documentService.list(CurrentUserHolder.require(), page, size));
    }

    @GetMapping("/{id}")
    public Result<DocumentVO> detail(@PathVariable Long id) {
        return Result.ok(documentService.detail(CurrentUserHolder.require(), id));
    }

    /** 预览解析出的文本，用来确认解析确实成功了 */
    @GetMapping("/{id}/text")
    public Result<String> text(@PathVariable Long id,
                               @RequestParam(defaultValue = "1000") int maxChars) {
        return Result.ok(documentService.previewText(CurrentUserHolder.require(), id, maxChars));
    }
}
