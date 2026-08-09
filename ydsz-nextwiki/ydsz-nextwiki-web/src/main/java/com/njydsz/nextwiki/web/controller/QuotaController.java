package com.njydsz.nextwiki.web.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.server.service.QuotaApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 存储配额 REST API Controller。
 *
 * <p>提供按用户 / 租户 / 项目维度的存储配额查询、设置和校验能力：
 * <ul>
 *   <li>{@code GET /quota/info?scopeType=...&scopeId=...} - 查询配额使用情况</li>
 *   <li>{@code POST /quota/set} - 设置配额（管理员操作）</li>
 * </ul>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>多维度配额：支持 user（个人）/ tenant（租户）/ project（项目）三种 scope 维度</li>
 *   <li>双限额控制：总容量（{@code quotaLimit}，字节）+ 文件数（{@code fileCountLimit}）双重限制</li>
 *   <li>实时校验：上传/创建文件夹前由 service 层实时校验配额，超额拒绝</li>
 *   <li>配额预警：前端可基于 usagePercent 展示配额预警 UI</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>设置配额接口加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有接口加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_QUOTA_*）</li>
 *   <li>设置配额是高权限操作，需 NEXTWIKI_QUOTA_SET 权限码</li>
 *   <li>配额计算由 service 层加分布式锁，避免并发场景下的超扣</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   GET  /api/v1/nextwiki/quota/info?scopeType=user&scopeId=xxx - 查询配额
 *   POST /api/v1/nextwiki/quota/set                            - 设置配额
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-server.QuotaApplicationService
 *                                            ↓
 *                                   ydsz-nextwiki-infra.StorageQuotaMapper
 *                                            ↓
 *                                   ydsz_storage_quota
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/quota")
@RequiredArgsConstructor
@Tag(name = "存储配额", description = "配额查询、设置、校验（支持 user/tenant/project 维度）")
public class QuotaController {

    /** 配额应用服务（封装配额查询 + 设置 + 实时校验） */
    private final QuotaApplicationService quotaApplicationService;

    /**
     * 查询指定 scope 的配额使用情况。
     *
     * <p>返回已用容量、文件数、限额、使用百分比等信息。
     *
     * @param scopeType 作用域类型（user/tenant/project）
     * @param scopeId   作用域 ID（用户 ID / 租户 ID / 项目 ID）
     * @return 统一响应结果，data 为 {@link StorageQuota}
     */
    @GetMapping("/info")
    @Operation(summary = "查询配额使用情况")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_QUOTA_VIEW)
    public BaseResponse<StorageQuota> getQuota(
            @RequestParam(defaultValue = "user") String scopeType,
            @RequestParam String scopeId) {
        return BaseResponse.success(quotaApplicationService.getQuotaInfo(scopeType, scopeId));
    }

    /**
     * 设置指定 scope 的配额（管理员操作）。
     *
     * <p>设置容量上限和文件数上限；新配额立即生效。
     * 注意：缩小配额不会主动清理已用空间，仅在后续上传/创建时触发拦截。
     *
     * @param request 设置请求（scopeType / scopeId / quotaLimit / fileCountLimit）
     * @param userId  操作人 ID（用于审计）
     * @return 统一响应结果，data 为设置后的 {@link StorageQuota}
     */
    @Idempotent(key = "ydsz:nextwiki:QuotaController:setQuota:lock", ttlSeconds = 5)
    @PostMapping("/set")
    @Operation(summary = "设置配额（管理员）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_QUOTA_SET)
    public BaseResponse<StorageQuota> setQuota(
            @Valid @RequestBody NextwikiDTOs.SetQuotaRequest request,
            @RequestHeader(HeaderConstants.X_USER_ID) String userId) {
        StorageQuota quota = quotaApplicationService.setQuota(
                request.getScopeType(),
                request.getScopeId(),
                request.getQuotaLimit(),
                request.getFileCountLimit(),
                userId);
        return BaseResponse.success(quota);
    }
}
