package com.njydsz.pmis.nextwiki.web.controller;

import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.pmis.nextwiki.domain.service.SearchDomainService;
import com.njydsz.pmis.nextwiki.domain.vo.SearchResultVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索 REST API
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/nextwiki/search")
@RequiredArgsConstructor
@Tag(name = "全文搜索", description = "文件�?内容/标签搜索")
public class SearchController {

    private final SearchDomainService searchDomainService;

    @PostMapping
    @Operation(summary = "综合搜索")
    public BaseResponse<SearchResultVO> search(
            @RequestBody NextwikiDTOs.SearchRequest request,
            @RequestHeader("X-User-Id") String userId) {

        int page = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
        String scope = request.getScope() != null ? request.getScope() : "all";

        SearchResultVO result = searchDomainService.search(
                request.getKeyword(), userId, scope, page, pageSize);
        return BaseResponse.ok(result);
    }

    @PostMapping("/rebuild")
    @Operation(summary = "重建全量索引")
    public BaseResponse<Void> rebuildIndices(@RequestHeader("X-User-Id") String userId) {
        searchDomainService.rebuildAllIndices();
        return BaseResponse.ok();
    }
}
