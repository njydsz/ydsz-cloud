package com.njydsz.nextwiki.web.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.vo.SearchResultVO;
import com.njydsz.nextwiki.server.service.SearchApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 搜索 REST API
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/search")
@RequiredArgsConstructor
@Tag(name = "全文搜索", description = "文件名/内容/标签搜索")
public class SearchController {

    private final SearchApplicationService searchApplicationService;

    @Idempotent(key = "nextwiki:search:search", ttlSeconds = 5, message = "请勿重复提交")
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
        return BaseResponse.success(result);
    }

    @Idempotent(key = "nextwiki:search:rebuildIndices", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/rebuild")
    @Operation(summary = "重建全量索引")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SEARCH_REBUILD)
    public BaseResponse<Void> rebuildIndices(@RequestHeader("X-User-Id") String userId) {
        searchApplicationService.rebuildAllIndices();
        return BaseResponse.success();
    }
}
