package com.njydsz.nextwiki.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.nextwiki.domain.dto.NextwikiDto;
import com.njydsz.nextwiki.domain.vo.ShareAccessLogVO;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;
import com.njydsz.nextwiki.domain.vo.ShareRecipientVO;
import com.njydsz.nextwiki.server.service.ShareApplicationService;

/**
 * 文件分享 REST API Controller。
 *
 * <p>提供文件分享链接的创建、验证、撤销、查询能力，是网盘对外分享功能的核心入口：
 *
 * <ul>
 *   <li>{@code POST /shares} - 创建分享链接（支持密码/提取码/过期/访问次数限制/定向分享）
 *   <li>{@code POST /shares/verify} - 验证分享链接访问权限（公开接口，需限流防爆破）
 *   <li>{@code DELETE /shares/{id}} - 撤销分享
 *   <li>{@code GET /shares/my} - 查询我创建的分享列表
 *   <li>{@code GET /shares/{shareId}/logs} - 查询分享访问日志
 *   <li>{@code GET /shares/{shareId}/recipients} - 查询分享目标用户
 *   <li>{@code GET /shares/received} - 查询我收到的分享
 * </ul>
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>分享链接：生成全局唯一 shareCode（短链形式），对外可访问
 *   <li>定向分享：支持指定目标用户，仅目标用户可访问（shareTargetType=USER）
 *   <li>密码保护：可选 password 字段，访问时需输入明文密码
 *   <li>访问日志：自动记录每次访问的 IP、UA、结果
 *   <li>到期提醒：定时任务扫描即将到期的分享并触发通知
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ApiVersion("v1")
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/shares")
@RequiredArgsConstructor
@Tag(name = "文件分享", description = "创建分享链接、验证访问、撤销分享、访问日志、定向分享")
public class ShareController {

  /** 分享应用服务（封装分享链接的 CRUD + 验证 + 撤销） */
  private final ShareApplicationService shareApplicationService;

  /**
   * 创建文件分享链接（支持定向分享）。
   *
   * <p>基于文件节点 ID 创建一条分享记录，并返回 shareCode（用于生成可访问的 URL）。 可选配置密码 / 提取码 / 过期时间 / 最大访问次数 / 目标用户 / 标题。
   *
   * @param request 创建分享请求
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 {@link ShareLinkVO}
   */
  @Audit(
      module = "分享管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'createShare'")
  @Idempotent(key = "ydsz:nextwiki:ShareController:createShare:lock", ttlSeconds = 5)
  @PostMapping
  @Operation(summary = "创建分享链接")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_CREATE)
  public YdszResponse<ShareLinkVO> createShare(
      @Valid @RequestBody NextwikiDto.CreateShareRequest request,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    ShareLinkVO result;
    // 有目标用户时使用定向分享模式
    if (request.getTargetUserIds() != null && !request.getTargetUserIds().isEmpty()) {
      result =
          shareApplicationService.createShareWithTargets(
              request.getFileNodeId(),
              request.getShareType(),
              request.getPassword(),
              request.getExpireTime(),
              request.getMaxAccessCount(),
              request.getTargetUserIds(),
              request.getTitle(),
              userId);
    } else {
      result =
          shareApplicationService.createShare(
              request.getFileNodeId(),
              request.getShareType(),
              request.getPassword(),
              request.getExpireTime(),
              request.getMaxAccessCount(),
              userId);
    }
    return YdszResponse.success(result);
  }

  /**
   * 验证分享链接的访问权限。
   *
   * <p>对外公开接口（无需登录），传入 shareCode + 提取码 + 密码进行三重校验。 验证通过后返回 {@link ShareLink}，同时记录访问日志。
   *
   * @param request 验证请求（shareCode / extractCode / password）
   * @param httpRequest HTTP 请求（用于获取 IP 和 UA）
   * @return 统一响应结果，data 为验证通过后的分享链接信息
   */
  @Audit(
      module = "分享管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'verifyAccess'")
  @Idempotent(key = "ydsz:nextwiki:ShareController:verifyAccess:lock", ttlSeconds = 5)
  @RateLimit(resource = "nextwiki.share.verifyAccess", threshold = 50)
  @PostMapping("/verify")
  @Operation(summary = "验证分享链接访问权限")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_VERIFY)
  public YdszResponse<ShareLinkVO> verifyAccess(
      @Valid @RequestBody NextwikiDto.VerifyShareRequest request, HttpServletRequest httpRequest) {
    ShareLinkVO result =
        shareApplicationService.verifyAccess(
            request.getShareCode(), request.getExtractCode(), request.getPassword());

    // 记录访问日志
    String visitorIp = getClientIp(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    shareApplicationService.recordAccessLog(
        result.getId(),
        result.getShareCode(),
        result.getFileNodeId(),
        null,
        visitorIp,
        userAgent,
        "VIEW",
        "SUCCESS",
        null);

    return YdszResponse.success(result);
  }

  /**
   * 撤销（删除）分享链接。
   *
   * <p>将分享链接标记为已撤销，访问时直接拒绝。仅分享创建者可撤销。
   *
   * @param shareId 分享链接 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果
   */
  @Audit(
      module = "分享管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'revoke'")
  @Idempotent(key = "ydsz:nextwiki:ShareController:revoke:lock", ttlSeconds = 5)
  @DeleteMapping("/{shareId}")
  @Operation(summary = "撤销分享")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_REVOKE)
  public YdszResponse<Void> revoke(
      @PathVariable String shareId, @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    shareApplicationService.revoke(shareId, userId);
    return YdszResponse.success();
  }

  /**
   * 查询当前用户创建的所有分享链接。
   *
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 {@link ShareLinkVO} 列表
   */
  @GetMapping("/my")
  @Operation(summary = "查询我的分享列表")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_LIST)
  public YdszResponse<List<ShareLinkVO>> myShares(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return YdszResponse.success(shareApplicationService.findByUserId(userId));
  }

  /**
   * 查询分享链接的访问日志。
   *
   * @param shareId 分享链接 ID
   * @param limit 返回条数限制（默认 50）
   * @param userId 当前用户 ID
   * @return 访问日志列表
   */
  @GetMapping("/{shareId}/logs")
  @Operation(summary = "查询分享访问日志")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_LOG_VIEW)
  public YdszResponse<List<ShareAccessLogVO>> getAccessLogs(
      @PathVariable String shareId,
      @RequestParam(defaultValue = "50") int limit,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return YdszResponse.success(shareApplicationService.getAccessLogs(shareId, limit));
  }

  /**
   * 查询分享链接的目标用户列表。
   *
   * @param shareId 分享链接 ID
   * @param userId 当前用户 ID
   * @return 目标用户列表
   */
  @GetMapping("/{shareId}/recipients")
  @Operation(summary = "查询分享目标用户")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_VIEW)
  public YdszResponse<List<ShareRecipientVO>> getRecipients(
      @PathVariable String shareId, @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return YdszResponse.success(shareApplicationService.getRecipients(shareId));
  }

  /**
   * 查询当前用户收到的分享列表。
   *
   * @param userId 当前用户 ID
   * @return 收到的分享列表
   */
  @GetMapping("/received")
  @Operation(summary = "查询我收到的分享")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_SHARE_LIST)
  public YdszResponse<List<ShareRecipientVO>> getReceivedShares(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
    return YdszResponse.success(shareApplicationService.getReceivedShares(userId));
  }

  // ==================== 私有方法 ====================

  /**
   * 获取客户端真实 IP（考虑代理）。
   *
   * @param request HTTP 请求
   * @return 客户端 IP 地址
   */
  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("X-Real-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getRemoteAddr();
    }
    // 多级代理场景：取第一个 IP
    if (ip != null && ip.contains(",")) {
      ip = ip.split(",")[0].trim();
    }
    return ip;
  }
}
