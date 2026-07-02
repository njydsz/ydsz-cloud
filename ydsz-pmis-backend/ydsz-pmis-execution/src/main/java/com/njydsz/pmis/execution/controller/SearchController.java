package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.es.ProjectSearchDoc;
import com.njydsz.pmis.execution.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全文检索 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "全文检索")
@RestController
@RequestMapping("/api/v1/execution/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 全文检索项目。
     *
     * @param keyword 搜索关键词
     * @param page    页码（从 0 开始）
     * @param size    每页条数
     * @return 搜索结果分页
     */
    @Operation(summary = "全文检索项目")
    @GetMapping("/projects")
    public Result<Page<ProjectSearchDoc>> searchProjects(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(searchService.searchProjects(keyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    /**
     * 重建所有索引。
     *
     * @return 重建结果提示
     */
    @Operation(summary = "重建索引")
    @PostMapping("/reindex")
    public Result<String> reindex() {
        searchService.reindexAll();
        return Result.ok("reindex started");
    }
}
