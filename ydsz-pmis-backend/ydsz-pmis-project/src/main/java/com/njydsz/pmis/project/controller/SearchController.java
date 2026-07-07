package com.njydsz.pmis.project.controller;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.util.SortBy;
import com.njydsz.pmis.project.search.ProjectSearchVO;
import com.njydsz.pmis.project.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/execution/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService searchService;

    /**
     * 全文检索项目。
     *
     * @param keyword 搜索关键词
     * @param page    页码（从 1 开始，与 PageQuery 约定一致）
     * @param size    每页条数
     * @return 搜索结果分页
     */
    @Operation(summary = "全文检索项目")
    @RateLimit(key = "search", qps = 10, windowSeconds = 60)
    @GetMapping("/projects")
    public Result<Page<ProjectSearchVO>> searchProjects(
            @RequestParam @NotBlank(message = "{validation.execution.msg_ede12b69}") String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.execution.msg_9aaebb77}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.execution.msg_15154512}") @Max(100) int size) {
        return Result.ok(searchService.searchProjects(keyword,
                PageRequest.of(page - 1, size, SortBy.desc(ProjectSearchVO::getCreatedAt))));
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
