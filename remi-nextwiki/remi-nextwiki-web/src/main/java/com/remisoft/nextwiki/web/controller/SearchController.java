package com.remisoft.nextwiki.web.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.common.auth.annotation.AuthApiPermission;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.permission.PermissionCodes;
import com.remisoft.nextwiki.api.dto.NextwikiDTOs;
import com.remisoft.nextwiki.domain.vo.SearchResultVO;
import com.remisoft.nextwiki.server.service.SearchApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.remisoft.common.lock.annotation.Idempotent;

/**
 * 全文搜索 REST API Controller。
 *
 * <p>提供网盘文件的综合搜索能力，支持多维度（文件名/路径/标签/内容）搜索：
 * <ul>
 *   <li>{@code POST /search} - 综合搜索（支持 scope 限定：name/content/tag/all）</li>
 *   <li>{@code POST /search/rebuild} - 重建全量索引（运维/修复用）</li>
 * </ul>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>多源适配：当 Elasticsearch 可用时走 ES 全文检索（高性能）；不可用时降级为数据库 LIKE 搜索</li>
 *   <li>多维度：支持按文件名、文件内容、标签、文件路径等维度搜索</li>
 *   <li>权限隔离：搜索结果按当前用户 ID 过滤，仅返回用户有权限访问的文件</li>
 *   <li>高亮显示：ES 模式下可返回关键词高亮信息（前端可高亮展示匹配片段）</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>搜索接口加 {@link Idempotent} 防重（5s TTL），避免重复请求</li>
 *   <li>所有接口加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_SEARCH*）</li>
 *   <li>重建索引是高耗时操作，仅管理员权限可触发</li>
 *   <li>用户身份通过 {@code X-User-Id} 请求头传递（由网关层注入）</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   POST /api/v1/nextwiki/search       - 综合搜索
 *   POST /api/v1/nextwiki/search/rebuild - 重建全量索引
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → remi-gateway → remi-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   remi-nextwiki-server.SearchApplicationService
 *                                       ├── WikiSearchProvider (ES 适配)
 *                                       └── SearchDomainService (DB LIKE 降级)
 *                                            ↓
 *                                   Elasticsearch / remi_search_index
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/search")
@RequiredArgsConstructor
@Tag(name = "全文搜索", description = "文件名/内容/标签综合搜索，支持 ES 全文检索与 DB LIKE 降级")
public class SearchController {

    /** 搜索应用服务（封装搜索逻辑 + 索引管理 + 降级策略） */
    private final SearchApplicationService searchApplicationService;

    /**
     * 综合搜索。
     *
     * <p>根据 keyword 在用户可见范围内搜索匹配的文件；scope 控制搜索维度（name/content/tag/all）。
     * 底层自动选择 ES（可用时）或 DB LIKE（降级）。
     *
     * @param request 搜索请求（keyword / scope / page / pageSize）
     * @param userId  当前用户 ID（用于权限过滤）
     * @return 统一响应结果，data 为 {@link SearchResultVO}（含结果列表 + 总数 + 高亮信息）
     */
    @Idempotent(key = "remi:nextwiki:SearchController:search:lock", ttlSeconds = 5)
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

    /**
     * 重建全量搜索索引。
     *
     * <p>运维操作：将所有可索引文件重新写入 ES / 全文索引表，用于：
     * <ul>
     *   <li>索引丢失/损坏后的恢复</li>
     *   <li>首次部署后初始化索引</li>
     *   <li>索引结构变更后的全量重建</li>
     * </ul>
     *
     * @param userId 当前用户 ID（用于审计）
     * @return 统一响应结果
     */
    @Idempotent(key = "remi:nextwiki:SearchController:rebuildIndices:lock", ttlSeconds = 5)
    @PostMapping("/rebuild")
    @Operation(summary = "重建全量索引")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SEARCH_REBUILD)
    public BaseResponse<Void> rebuildIndices(@RequestHeader("X-User-Id") String userId) {
        searchApplicationService.rebuildAllIndices();
        return BaseResponse.success();
    }
}
