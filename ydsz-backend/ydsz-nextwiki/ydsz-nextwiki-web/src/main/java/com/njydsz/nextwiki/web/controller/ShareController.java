package com.njydsz.nextwiki.web.controller;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.server.service.ShareApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 文件分享 REST API
 *
 * <p>提供文件分享链接的创建、访问验证、撤销等操作。
 * 支持密码保护、提取码验证、过期时间和访问次数限制。
 *
 * <p><b>接口路径：</b>{@code /api/v1/nextwiki/shares}
 *
 * <p><b>安全特性：</b>写接口启用 {@link Idempotent} 防重复、{@link Audit} 审计日志、
 * {@link AuthApiPermission} 权限码校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/shares")
@RequiredArgsConstructor
@Tag(name = "文件分享", description = "创建分享链接、验证访问、撤销分享")
public class ShareController {

    private final ShareApplicationService shareApplicationService;

    @Audit(module = "分享管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'createShare'")
    @Idempotent(key = "ydsz:nextwiki:ShareController:createShare:lock", ttlSeconds = 5)
    @PostMapping
    @Operation(summary = "创建分享链接")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_CREATE)
    public BaseResponse<ShareLink> createShare(
            @RequestBody NextwikiDTOs.CreateShareRequest request,
            @RequestHeader("X-User-Id") String userId) {

        ShareLink result = shareApplicationService.createShare(
                request.getFileNodeId(),
                request.getShareType(),
                request.getPassword(),
                request.getExpireTime(),
                request.getMaxAccessCount(),
                userId);
        return BaseResponse.success(result);
    }

    @Audit(module = "分享管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'verifyAccess'")
    @Idempotent(key = "ydsz:nextwiki:ShareController:verifyAccess:lock", ttlSeconds = 5)
    @RateLimit(resource = "nextwiki.share.verifyAccess", threshold = 50)
    @PostMapping("/verify")
    @Operation(summary = "验证分享链接访问权限")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_VERIFY)
    public BaseResponse<ShareLink> verifyAccess(@RequestBody NextwikiDTOs.VerifyShareRequest request) {
        ShareLink result = shareApplicationService.verifyAccess(
                request.getShareCode(),
                request.getExtractCode(),
                request.getPassword());
        return BaseResponse.success(result);
    }

    @Audit(module = "分享管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'revoke'")
    @Idempotent(key = "ydsz:nextwiki:ShareController:revoke:lock", ttlSeconds = 5)
    @DeleteMapping("/{shareId}")
    @Operation(summary = "撤销分享")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_REVOKE)
    public BaseResponse<Void> revoke(
            @PathVariable String shareId,
            @RequestHeader("X-User-Id") String userId) {

        shareApplicationService.revoke(shareId, userId);
        return BaseResponse.success();
    }

    @GetMapping("/my")
    @Operation(summary = "查询我的分享列表")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_LIST)
    public BaseResponse<List<ShareLink>> myShares(@RequestHeader("X-User-Id") String userId) {
        return BaseResponse.success(shareApplicationService.findByUserId(userId));
    }
}
