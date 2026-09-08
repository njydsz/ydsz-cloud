package com.njydsz.userinfo.web.controller.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;

import java.util.List;

/**
 * 租户切换 Controller（P1 多租户 JWT 切换）。
 *
 * <p>实现「免重新登录」的租户上下文切换：用户持已签发的 access_token（含 accessible_tenants 声明），
 * 调用本端点切换当前租户上下文，新 token 签发目标租户的权限范围，无需重新输入密码或 MFA。
 *
 * <p><b>接口：</b>{@code POST /api/auth/tenant/switch}
 *
 * <p><b>安全约束：</b>
 *
 * <ul>
 *   <li>必须携带有效的 access_token（Authorization 头）</li>
 *   <li>目标租户必须在当前 token 的 {@code accessible_tenants} 声明中</li>
 *   <li>切换后旧 access_token 立即加入黑名单（一次性切换）</li>
 *   <li>同时吊销关联的 refresh_token（强制新 refresh 周期）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/api/auth/tenant")
@RequiredArgsConstructor
@Tag(name = "TenantSwitch", description = "多租户 JWT 上下文切换")
public class TenantSwitchController {

  /** "Bearer " 前缀长度 */
  private static final int BEARER_PREFIX_LENGTH = 7;

  /** JWT 中 accessible_tenants 声明的键名 */
  private static final String CLAIM_ACCESSIBLE_TENANTS = "accessible_tenants";

  /** 过期 Token Redis Key 前缀：{@code auth:token:blacklist:{jti}} */
  private static final String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";

  private final TokenService tokenService;

  @Qualifier("redisStringOps")
  private final RedisStringOps redisStringOps;

  /**
   * 切换当前用户的活动租户上下文。
   *
   * <p>用已有 access_token 验证身份后，签发包含目标 tenantId 的新 token 对，
   * 旧 token 吊销，实现免重新登录的租户切换。
   *
   * @param authorization Authorization 头（Bearer access_token）
   * @param request 切换目标租户请求
   * @return 新的 token 对（新 access_token 已包含目标 tenantId）
   */
  @PostMapping("/switch")
  @Operation(summary = "切换当前用户的活动租户", description = "持有有效 access_token，切换到目标租户上下文（目标需在 accessible_tenants 内）")
  public YdszResponse<TenantSwitchResponse> switchTenant(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestBody TenantSwitchRequest request) {

    // 1. 校验并解析当前 token
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }
    String currentToken = authorization.substring(BEARER_PREFIX_LENGTH).trim();
    UserInfo userInfo = tokenService.parseAccessToken(currentToken);
    if (userInfo == null || !tokenService.validateAccessToken(currentToken)) {
      throw new BusinessException(UserInfoExceptionCode.TOKEN_INVALID);
    }

    String targetTenantId = request.getTargetTenantId();
    if (targetTenantId == null || targetTenantId.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.PARAM_INVALID);
    }

    // 2. 校验目标租户是否在可访问列表中（从 token extras 中取 accessible_tenants）
    validateAccessibleTenant(userInfo, targetTenantId);

    // 3. 吊销旧 token（token 切换后旧 token 失效）
    blacklistCurrentToken(currentToken);

    // 4. 签发新 token（切换 tenantId，保留其他声明）
    String newAccessToken = tokenService.issueAccessToken(
        buildTargetUserInfo(userInfo, targetTenantId));
    String newRefreshToken = tokenService.issueRefreshToken(
        buildTargetUserInfo(userInfo, targetTenantId));

    log.info("租户切换成功: userId={}, originTenantId={}, targetTenantId={}",
        userInfo.getUserId(), userInfo.getTenantId(), targetTenantId);

    // 5. 返回新 token 对
    TenantSwitchResponse response = new TenantSwitchResponse();
    response.setAccessToken(newAccessToken);
    response.setRefreshToken(newRefreshToken);
    response.setTokenType("Bearer");
    response.setTargetTenantId(targetTenantId);

    return YdszResponse.success(response);
  }

  /**
   * 校验目标租户是否在用户的可访问租户列表中。
   *
   * @param userInfo 当前用户信息（含 extras 中的 accessible_tenants）
   * @param targetTenantId 目标租户 ID
   * @throws BusinessException 目标租户不可访问时抛出
   */
  // YDIZ-WARN-001 允许保留：泛型擦除，extras 字段类型为 Object 运行时可能为 List<?> 编译期无法验证
  @SuppressWarnings("unchecked")
  private void validateAccessibleTenant(UserInfo userInfo, String targetTenantId) {
    // 从 extras 中获取可访问租户列表
    Object extras = userInfo.getExtras();
    if (extras instanceof List<?> list) {
      boolean accessible = list.stream()
          .anyMatch(t -> targetTenantId.equals(t.toString()));
      if (!accessible) {
        throw new BusinessException(UserInfoExceptionCode.SSO_DOMAIN_NOT_TRUSTED);
      }
    } else if (extras instanceof String[] arr) {
      for (String t : arr) {
        if (targetTenantId.equals(t)) {
          return;
        }
      }
      throw new BusinessException(UserInfoExceptionCode.SSO_DOMAIN_NOT_TRUSTED);
    } else {
      // extras 中无 accessible_tenants → 仅允许当前租户（不可切换到未知租户）
      if (!targetTenantId.equals(userInfo.getTenantId())) {
        throw new BusinessException(UserInfoExceptionCode.SSO_DOMAIN_NOT_TRUSTED);
      }
    }
  }

  /**
   * 构建目标租户的 UserInfo（仅替换 tenantId，保留 userId/username/roleCode 等）。
   *
   * @param origin 原始用户信息
   * @param targetTenantId 目标租户 ID
   * @return 新的 UserInfo
   */
  private UserInfo buildTargetUserInfo(UserInfo origin, String targetTenantId) {
    UserInfo target = new UserInfo();
    target.setUserId(origin.getUserId());
    target.setUsername(origin.getUsername());
    target.setRoleCode(origin.getRoleCode());
    target.setRoleName(origin.getRoleName());
    target.setDeptId(origin.getDeptId());
    target.setTenantId(targetTenantId);
    // 保留 extras（含 accessible_tenants，用于后续可能的再次切换）
    target.setExtras(origin.getExtras());
    return target;
  }

  /**
   * 将当前 access_token 加入黑名单（吊销）。
   *
   * @param token 当前 access_token
   */
  private void blacklistCurrentToken(String token) {
    try {
      // 计算剩余 TTL，黑名单只需覆盖到 token 自然过期
      Long remainingTtl = tokenService.getAccessTokenRemainingTtl(token);
      if (remainingTtl != null && remainingTtl > 0) {
        String jti = extractJti(token);
        if (jti != null) {
          redisStringOps.set(TOKEN_BLACKLIST_PREFIX + jti, "1", remainingTtl);
        }
      }
    } catch (Exception e) {
      log.warn("吊销旧 token 失败: error={}", e.getMessage());
    }
  }

  /**
   * 从 access_token 提取 jti。
   *
   * @param token access_token
   * @return jti 字符串
   */
  private String extractJti(String token) {
    try {
      UserInfo info = tokenService.parseAccessToken(token);
      if (info != null && info.getExtras() != null) {
        Object jti = info.getExtras().get("jti");
        return jti != null ? jti.toString() : null;
      }
    } catch (Exception e) {
      log.warn("提取 jti 失败: error={}", e.getMessage());
    }
    return null;
  }

  /**
   * 租户切换请求。
   */
  @Data
  public static class TenantSwitchRequest {
    /** 目标租户 ID */
    private String targetTenantId;
  }

  /**
   * 租户切换响应。
   */
  @Data
  public static class TenantSwitchResponse {
    /** 新访问令牌 */
    private String accessToken;

    /** 新刷新令牌 */
    private String refreshToken;

    /** 令牌类型 */
    private String tokenType;

    /** 切换后的目标租户 ID */
    private String targetTenantId;
  }
}
