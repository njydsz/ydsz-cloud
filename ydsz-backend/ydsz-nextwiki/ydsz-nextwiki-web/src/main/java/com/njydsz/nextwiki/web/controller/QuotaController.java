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
 * 存储配额 REST API
 *
 * <p>提供按用户/租户/项目维度的存储配额查询、设置和校验。
 *
 * <p><b>接口路径：</b>{@code /api/v1/nextwiki/quota}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/quota")
@RequiredArgsConstructor
@Tag(name = "存储配额", description = "配额查询、设置、校验")
public class QuotaController {

    private final QuotaApplicationService quotaApplicationService;

    @GetMapping("/info")
    @Operation(summary = "查询配额使用情况")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_QUOTA_VIEW)
    public BaseResponse<StorageQuota> getQuota(
            @RequestParam(defaultValue = "user") String scopeType,
            @RequestParam String scopeId) {
        return BaseResponse.success(quotaApplicationService.getQuotaInfo(scopeType, scopeId));
    }

    @Idempotent(key = "ydsz:nextwiki:QuotaController:setQuota:lock", ttlSeconds = 5)
    @PostMapping("/set")
    @Operation(summary = "设置配额（管理员）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_QUOTA_SET)
    public BaseResponse<StorageQuota> setQuota(
            @Valid @RequestBody NextwikiDTOs.SetQuotaRequest request,
            @RequestHeader("X-User-Id") String userId) {
        StorageQuota quota = quotaApplicationService.setQuota(
                request.getScopeType(),
                request.getScopeId(),
                request.getQuotaLimit(),
                request.getFileCountLimit(),
                userId);
        return BaseResponse.success(quota);
    }
}
