package com.njydsz.pmis.nextwiki.web.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.pmis.nextwiki.domain.vo.SearchResultVO;
import com.njydsz.pmis.nextwiki.server.service.SearchApplicationService;

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
@RequestMapping("/api/v1/nextwiki/search")
@RequiredArgsConstructor
@Tag(name = "全文搜索", description = "文件名/内容/标签搜索")
public class SearchController {

    private final SearchApplicationService searchApplicationService;

    @PostMapping
    @Operation(summary = "综合搜索")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SEARCH)
    public BaseResponse<SearchResultVO> search(
            @Valid @RequestBody NextwikiDTOs.SearchRequest request,
            @RequestHeader("X-User-Id") String userId) {

        int page = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
        String scope = request.getScope() != null ? request.getScope() : "all";

        SearchResultVO result = searchApplicationService.search(
                request.getKeyword(), userId, scope, page, pageSize);
        return BaseResponse.ok(result);
    }

    @PostMapping("/rebuild")
    @Operation(summary = "重建全量索引")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SEARCH_REBUILD)
    public BaseResponse<Void> rebuildIndices(@RequestHeader("X-User-Id") String userId) {
        searchApplicationService.rebuildAllIndices();
        return BaseResponse.ok();
    }
}
