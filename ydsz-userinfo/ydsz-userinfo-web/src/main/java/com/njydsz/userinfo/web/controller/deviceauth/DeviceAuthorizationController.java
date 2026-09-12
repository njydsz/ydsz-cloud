package com.njydsz.userinfo.web.controller.deviceauth;

import java.security.SecureRandom;
import java.util.Base64;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * OAuth2 设备授权码模式 Controller（RFC 8628 Device Authorization Grant）。
 *
 * <p>实现设备授权码流程，适用于输入受限的设备（智能电视、CLI 工具、打印机等）。
 *
 * <p><b>流程（RFC 8628 §3.1 - 3.2）：</b>
 *
 * <ol>
 *   <li>设备调用 {@code POST /api/oauth2/device_authorization} 获取 device_code + user_code</li>
 *   <li>设备展示 user_code 和 verification_uri，引导用户在浏览器中授权</li>
 *   <li>设备轮询 {@code POST /api/oauth2/token}（grant_type=device_code）检查授权状态</li>
 *   <li>用户授权后，轮询请求返回 access_token</li>
 * </ol>
 *
 * <p><b>端点：</b>
 *
 * <ul>
 *   <li>{@code POST /api/oauth2/device_authorization} — 获取设备码</li>
 *   <li>{@code POST /api/oauth2/token?grant_type=urn:ietf:params:oauth:grant-type:device_code} — 轮询获取 token</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/api/oauth2")
@RequiredArgsConstructor
@Tag(name = "DeviceAuthorization", description = "OAuth2 设备授权码模式 (RFC 8628)")
public class DeviceAuthorizationController {

  /** 集合初始容量 */
  private static final int MAP_CAPACITY = 16;

  /** User-Code 分组位置（连字符在第 4 位之后插入） */
  private static final int USER_CODE_GROUP_SIZE = 4;

  /** User-Code 字符集（去除易混淆字符：0/O/1/I/L） */
  private static final char[] USER_CODE_CHARS =
      "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

  /** User-Code 长度（8 字符，格式：XXXX-XXXX） */
  private static final int USER_CODE_LENGTH = 8;

  /** Device-Code 随机字节长度（32 字节 = 256 位熵） */
  private static final int DEVICE_CODE_RANDOM_BYTES = 32;

  /** device_code 有效期（秒）：15 分钟（RFC 8628 §3.2 建议值） */
  private static final long DEVICE_CODE_TTL_SECONDS = 900L;

  /** user_code 有效期（秒）：与 device_code 一致 */
  private static final long USER_CODE_TTL_SECONDS = 900L;

  /** 轮询间隔最小值（秒）：5 秒（RFC 8628 §3.2 recommends ≥ 5 seconds） */
  private static final long MIN_POLL_INTERVAL_SECONDS = 5L;

  /** 最大授权请求数（pending 状态）：每个设备每次只能有一个活跃请求 */
  private static final int MAX_PENDING_REQUESTS = 1;

  /** device_code Redis Key 前缀：{@code oauth2:device:code:{deviceCode}} */
  private static final String DEVICE_CODE_KEY_PREFIX = "oauth2:device:code:";

  /** user_code → device_code 映射 Redis Key 前缀：{@code oauth2:device:usercode:{userCode}} */
  private static final String USER_CODE_KEY_PREFIX = "oauth2:device:usercode:";

  /** verification_uri（用户完成授权的页面 URL） */
  private static final String VERIFICATION_URI = "/device-login";

  /** OAuth2 grant_type for device_code flow (RFC 8628 §3.4) */
  public static final String GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

  /** 设备码状态：等待用户授权 */
  private static final String STATUS_PENDING = "authorization_pending";

  /** 设备码状态：用户已批准 */
  private static final String STATUS_APPROVED = "approved";

  /** 设备码状态：用户已拒绝 */
  private static final String STATUS_DENIED = "access_denied";

  /** 设备码状态：device_code 已过期 */
  private static final String STATUS_EXPIRED = "expired_token";

  /** 设备码状态：轮询过快 */
  private static final String STATUS_SLOW_DOWN = "slow_down";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RedisStringOps redisStringOps;
  private final UserInfoProperties properties;

  /**
   * 设备授权请求端点（RFC 8628 §3.1）。
   *
   * <p>设备调用此接口获取 device_code 和 user_code，然后展示 user_code
   * 和 verification_uri 给用户，引导用户在浏览器中完成授权。
   *
   * @param clientId 客户端 ID（必须已在 ydsz.userinfo.oauth2.clients 中注册）
   * @param scope 授权范围（可选，如 "openid profile"）
   * @return 设备授权响应（含 device_code、user_code、verification_uri）
   */
  @PostMapping("/device_authorization")
  @RateLimit(resource = "userinfo.oauth2.device_auth", threshold = 10)
  @Operation(summary = "设备授权请求", description = "获取 device_code 和 user_code，供受限设备完成 OAuth2 授权")
  public YdszResponse<DeviceAuthorizationResponse> deviceAuthorization(
      @RequestParam("client_id") String clientId,
      @RequestParam(value = "scope", required = false) String scope) {

    // 1. 校验 clientId
    validateClientId(clientId);

    // 2. 生成 device_code（高熵随机串）+ user_code（易输入短码）
    String deviceCode = generateDeviceCode();
    String userCode = generateUserCode();

    // 3. 构建设备授权上下文
    DeviceCodeContext context = DeviceCodeContext.builder()
        .clientId(clientId)
        .scope(scope)
        .status(STATUS_PENDING)
        .createdAt(System.currentTimeMillis())
        .build();

    // 4. 持久化到 Redis（device_code 和 user_code 双向映射）
    redisStringOps.set(
        DEVICE_CODE_KEY_PREFIX + deviceCode,
        context.toJson(),
        DEVICE_CODE_TTL_SECONDS);
    redisStringOps.set(
        USER_CODE_KEY_PREFIX + userCode,
        deviceCode,
        USER_CODE_TTL_SECONDS);

    log.info("Device authorization requested: clientId={}, userCode={}", clientId, userCode);

    // 5. 构建 RFC 8628 标准响应
    DeviceAuthorizationResponse response = new DeviceAuthorizationResponse();
    response.setDeviceCode(deviceCode);
    response.setUserCode(userCode);
    response.setVerificationUri(VERIFICATION_URI);
    response.setVerificationUriComplete(VERIFICATION_URI + "?user_code=" + userCode);
    response.setExpiresIn(DEVICE_CODE_TTL_SECONDS);
    response.setInterval(MIN_POLL_INTERVAL_SECONDS);

    return YdszResponse.success(response);
  }

  /**
   * 验证客户端 ID 是否已注册。
   *
   * @param clientId 客户端 ID
   * @throws BusinessException 客户端未注册时抛出
   */
  private void validateClientId(String clientId) {
    if (clientId == null || clientId.isBlank()
        || properties.getOauth2Clients() == null
        || !properties.getOauth2Clients().containsKey(clientId)) {
      throw new BusinessException(UserInfoExceptionCode.OAUTH2_CLIENT_INVALID);
    }
  }

  /**
   * 生成 device_code（高熵随机串，Base64URL 编码）。
   *
   * @return device_code
   */
  private String generateDeviceCode() {
    byte[] bytes = new byte[DEVICE_CODE_RANDOM_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * 生成 user_code（大写字母 + 数字，排除易混淆字符）。
   *
   * <p>格式：{@code XXXX-XXXX}（两组 4 字符，中间用连字符分隔）。
   *
   * @return user_code
   */
  private String generateUserCode() {
    StringBuilder sb = new StringBuilder(USER_CODE_LENGTH + 1);
    for (int i = 0; i < USER_CODE_LENGTH; i++) {
      if (i == USER_CODE_GROUP_SIZE) {
        sb.append('-');
      }
      int index = SECURE_RANDOM.nextInt(USER_CODE_CHARS.length);
      sb.append(USER_CODE_CHARS[index]);
    }
    return sb.toString();
  }

  /**
   * RFC 8628 设备授权响应（符合 RFC 8628 §3.2 响应格式）。
   */
  @Data
  public static class DeviceAuthorizationResponse {
    /** 设备码（高熵随机串，设备本地存储） */
    private String deviceCode;

    /** 用户码（用户输入到浏览器页面的短码） */
    private String userCode;

    /** 用户在浏览器中访问的授权页面 URL */
    private String verificationUri;

    /** 完整验证 URL（自动拼接 user_code，支持预填充） */
    private String verificationUriComplete;

    /** 设备码/用户码有效期（秒） */
    private long expiresIn;

    /** 设备轮询 token 端点的最小间隔（秒） */
    private long interval;
  }

  /**
   * 设备码上下文（Redis 持久化）。
   */
  @Data
  @Builder
  public static class DeviceCodeContext {
    /** 客户端 ID */
    private String clientId;

    /** 授权范围 */
    private String scope;

    /** 当前状态（pending/approved/denied） */
    private String status;

    /** 创建时间戳（毫秒） */
    private long createdAt;

    /** 授权用户 ID（approved 后填入） */
    private String userId;

    /** 授权用户名（approved 后填入） */
    private String username;

    /**
     * 序列化为 JSON 字符串。
     *
     * @return JSON 字符串
     */
    public String toJson() {
      // 简化实现：使用 Map 序列化
      // 生产环境可替换为 YdszJson 工具
      return String.format(
          "{\"clientId\":\"%s\",\"scope\":\"%s\",\"status\":\"%s\",\"createdAt\":%d,\"userId\":\"%s\",\"username\":\"%s\"}",
          nvl(clientId), nvl(scope), nvl(status), createdAt, nvl(userId), nvl(username));
    }

    /**
     * 安全取非空字符串。
     */
    private static String nvl(String s) {
      return s != null ? s : "";
    }
  }

  /**
   * 设备码轮询响应常量。
   */
  public static class PollingResponses {
    /** 仍在等待用户授权 */
    public static final String AUTHORIZATION_PENDING = "authorization_pending";

    /** 轮询过快，需减速 */
    public static final String SLOW_DOWN = "slow_down";

    /** 用户已拒绝 */
    public static final String ACCESS_DENIED = "access_denied";

    /** device_code 无效或不存在 */
    public static final String EXPIRED_TOKEN = "expired_token";
  }
}
