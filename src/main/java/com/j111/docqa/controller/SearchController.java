package com.j111.docqa.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.j111.docqa.common.Result;
import com.j111.docqa.dto.SearchHit;
import com.j111.docqa.dto.SearchRequest;
import com.j111.docqa.security.CurrentUserHolder;
import com.j111.docqa.service.SearchService;

import jakarta.validation.Valid;

/**
 * 检索接口：先单独暴露出来，方便验证"切分 + 向量化 + 检索"这一段是否靠谱；
 * 后面加 /api/chat 时，内部复用同一套检索逻辑。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public Result<List<SearchHit>> search(@Valid @RequestBody SearchRequest request) {
        return Result.ok(searchService.search(
                CurrentUserHolder.require(), request.query(), request.topK()));
    }
}
