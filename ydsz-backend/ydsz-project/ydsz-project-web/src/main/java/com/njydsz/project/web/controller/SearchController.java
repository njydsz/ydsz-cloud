package com.njydsz.project.web.controller.common;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.annotation.RateLimit;
import com.njydsz.project.domain.query.ProjectSearchVO;
import com.njydsz.project.domain.query.UniversalSearchVO;
import com.njydsz.project.server.service.SearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 全文检索 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "全文检索")
@RestController
@RequestMapping("/api/project/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    /** 全局搜索服务 */
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
    public BaseResponse<Page<ProjectSearchVO>> searchProjects(
            @RequestParam @NotBlank(message = "{validation.execution.msg_ede12b69}") String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.execution.msg_9aaebb77}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.execution.msg_15154512}") @Max(100) int size) {
        return BaseResponse.success(searchService.searchProjects(keyword,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    /**
     * 重建所有索引。
     *
     * @return 重建结果提示
     */
    @Operation(summary = "重建索引")
    @Idempotent(key = "search:reindex", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/reindex")
    public BaseResponse<String> reindex() {
        searchService.reindexAll();
        return BaseResponse.success("reindex started");
    }

    /**
     * 统一搜索（跨实体）。
     *
     * <p>一次请求搜索项目 / 合同 / 审批 / 工单 / 人员 / 知识库等实体，
     * 按实体类型分组返回，每类最多 {@code size} 条。
     *
     * @param keyword 搜索关键词
     * @param size    每类实体最大返回条数（默认 5）
     * @return 统一搜索结果列表
     */
    @Operation(summary = "统一搜索（跨实体）")
    @RateLimit(key = "search-all", qps = 10, windowSeconds = 60)
    @GetMapping("/all")
    public BaseResponse<List<UniversalSearchVO>> searchAll(
            @RequestParam @NotBlank(message = "{validation.execution.msg_ede12b69}") String keyword,
            @RequestParam(defaultValue = "5") @Min(value = 1, message = "{validation.execution.msg_15154512}") @Max(20) int size) {
        return BaseResponse.success(searchService.searchAll(keyword, size));
    }
}
