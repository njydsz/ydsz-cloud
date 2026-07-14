package com.njydsz.pmis.nextwiki.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.pmis.nextwiki.domain.entity.ShareLink;
import com.njydsz.pmis.nextwiki.domain.service.ShareDomainService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件分享 REST API
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/shares")
@RequiredArgsConstructor
@Tag(name = "文件分享", description = "创建分享链接、验证访问、撤销分享")
public class ShareController {

    private final ShareDomainService shareDomainService;

    @PostMapping
    @Operation(summary = "创建分享链接")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_CREATE)
    @OperationLog(module = "网盘", action = "创建分享", bizType = "SHARE", saveResult = true)
    public BaseResponse<ShareLink> createShare(
            @RequestBody NextwikiDTOs.CreateShareRequest request,
            @RequestHeader("X-User-Id") String userId) {

        ShareLink result = shareDomainService.createShare(
                request.getFileNodeId(),
                request.getShareType(),
                request.getPassword(),
                request.getExpireTime(),
                request.getMaxAccessCount(),
                userId);
        return BaseResponse.ok(result);
    }

    @PostMapping("/verify")
    @Operation(summary = "验证分享链接访问权限")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_VERIFY)
    public BaseResponse<ShareLink> verifyAccess(@RequestBody NextwikiDTOs.VerifyShareRequest request) {
        ShareLink result = shareDomainService.verifyAccess(
                request.getShareCode(),
                request.getExtractCode(),
                request.getPassword());
        return BaseResponse.ok(result);
    }

    @DeleteMapping("/{shareId}")
    @Operation(summary = "撤销分享")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_REVOKE)
    @OperationLog(module = "网盘", action = "撤销分享", bizType = "SHARE")
    public BaseResponse<Void> revoke(
            @PathVariable String shareId,
            @RequestHeader("X-User-Id") String userId) {

        shareDomainService.revoke(shareId, userId);
        return BaseResponse.ok();
    }

    @GetMapping("/my")
    @Operation(summary = "查询我的分享列表")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_LIST)
    public BaseResponse<List<ShareLink>> myShares(@RequestHeader("X-User-Id") String userId) {
        return BaseResponse.ok(shareDomainService.findByUserId(userId));
    }
}
