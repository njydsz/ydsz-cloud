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
 * 文件分享 REST API Controller。
 *
 * <p>提供文件分享链接的创建、验证、撤销、查询能力，是网盘对外分享功能的核心入口：
 * <ul>
 *   <li>{@code POST /shares} - 创建分享链接（支持密码/提取码/过期/访问次数限制）</li>
 *   <li>{@code POST /shares/verify} - 验证分享链接访问权限（公开接口，需限流防爆破）</li>
 *   <li>{@code DELETE /shares/{id}} - 撤销分享</li>
 *   <li>{@code GET /shares/my} - 查询我创建的分享列表</li>
 * </ul>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>分享链接：生成全局唯一 shareCode（短链形式），对外可匿名访问</li>
 *   <li>密码保护：可选 password 字段，访问时需输入明文密码</li>
 *   <li>提取码：4-6 位数字提取码（区别于密码，作为辅助验证）</li>
 *   <li>过期控制：expireTime 控制分享链接的有效期</li>
 *   <li>访问次数：maxAccessCount 控制最大访问次数，到达上限后链接失效</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志</li>
 *   <li>公开验证接口加 {@link RateLimit} 限流（50 QPS）防密码爆破</li>
 *   <li>所有接口均加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_SHARE_*）</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   POST   /api/v1/nextwiki/shares         - 创建分享链接
 *   POST   /api/v1/nextwiki/shares/verify  - 验证分享链接
 *   DELETE /api/v1/nextwiki/shares/{id}    - 撤销分享
 *   GET    /api/v1/nextwiki/shares/my      - 我的分享列表
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                          ↓
 *                                  ydsz-nextwiki-server.ShareApplicationService
 *                                          ↓
 *                                  ydsz-nextwiki-infra.ShareLinkMapper
 *                                          ↓
 *                                  ydsz_share_link
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/shares")
@RequiredArgsConstructor
@Tag(name = "文件分享", description = "创建分享链接、验证访问、撤销分享、我的分享列表")
public class ShareController {

    /** 分享应用服务（封装分享链接的 CRUD + 验证 + 撤销） */
    private final ShareApplicationService shareApplicationService;

    /**
     * 创建文件分享链接。
     *
     * <p>基于文件节点 ID 创建一条分享记录，并返回 shareCode（用于生成可访问的 URL）。
     * 可选配置密码 / 提取码 / 过期时间 / 最大访问次数。
     *
     * @param request 创建分享请求（fileNodeId / shareType / password / expireTime / maxAccessCount）
     * @param userId  当前用户 ID
     * @return 统一响应结果，data 为 {@link ShareLink}（含 shareCode / expireTime 等）
     */
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

    /**
     * 验证分享链接的访问权限。
     *
     * <p>对外公开接口（无需登录），传入 shareCode + 提取码 + 密码进行三重校验。
     * 验证通过后返回 {@link ShareLink}，前端基于此访问分享内容。访问次数会自动 +1。
     *
     * @param request 验证请求（shareCode / extractCode / password）
     * @return 统一响应结果，data 为验证通过后的分享链接信息
     */
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

    /**
     * 撤销（删除）分享链接。
     *
     * <p>将分享链接标记为已撤销，访问时直接拒绝。仅分享创建者可撤销。
     *
     * @param shareId 分享链接 ID
     * @param userId  当前用户 ID
     * @return 统一响应结果
     */
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

    /**
     * 查询当前用户创建的所有分享链接。
     *
     * <p>按创建时间倒序返回，供"我的分享"列表页面展示。
     *
     * @param userId 当前用户 ID
     * @return 统一响应结果，data 为 {@link ShareLink} 列表
     */
    @GetMapping("/my")
    @Operation(summary = "查询我的分享列表")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_LIST)
    public BaseResponse<List<ShareLink>> myShares(@RequestHeader("X-User-Id") String userId) {
        return BaseResponse.success(shareApplicationService.findByUserId(userId));
    }
}
