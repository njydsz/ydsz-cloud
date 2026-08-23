package com.njydsz.userinfo.server.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.config.SocialAuthProperties;
import com.njydsz.userinfo.domain.dto.SocialAccountDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.SocialAccountRepository;
import com.njydsz.userinfo.domain.social.SocialAuthException;
import com.njydsz.userinfo.domain.social.SocialAuthProvider;
import com.njydsz.userinfo.domain.social.SocialUserInfo;
import com.njydsz.userinfo.domain.vo.SocialAccountVO;

/**
 * 社交认证服务编排实现。
 *
 * <p>负责社交账号绑定/解绑、社交登录回调处理、授权 URL 生成等核心编排逻辑。
 * 通过 {@link SocialAuthProvider} 统一抽象层屏蔽不同 OAuth2 平台差异。
 *
 * <p><b>核心流程（callback）：</b>校验平台开关 → 获取平台 Provider → 换取 token → 获取用户信息 →
 * 查找已有绑定 → 返回登录结果（已绑定）或仅返回社交信息（待绑定）。
 *
 * <p><b>核心流程（bind）：</b>校验平台开关 → 检查是否已绑定 → 保存绑定记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthService {

  /** State Redis Key 前缀（用于 CSRF 防护） */
  private static final String STATE_KEY_PREFIX = "userinfo:social:state:";

  /** State 有效期（秒）：10 分钟 */
  private static final long STATE_TTL_SECONDS = 600;

  private final SocialAuthProperties socialAuthProperties;
  private final SocialAccountRepository socialAccountRepository;
  private final Map<String, SocialAuthProvider> socialAuthProviderMap;
  private final RedisStringOps redisStringOps;

  /**
   * 生成指定平台的授权 URL。
   *
   * <p>前端通过此 URL 跳转至平台授权页面，用户完成授权后平台回调至 redirectUri。
   *
   * @param platform 平台标识（如 WECHAT/GITHUB）
   * @param redirectUri 授权完成后重定向回应用的回调地址
   * @return 完整的平台授权页面 URL
   * @throws BusinessException 社交认证未开启或平台不支持时抛出
   */
  public String buildAuthorizeUrl(String platform, String redirectUri) {
    checkSocialAuthEnabled();
    SocialAuthProvider provider = getProviderOrThrow(platform);
    String state = generateState();
    return provider.authorize(state, redirectUri);
  }

  /**
   * 处理社交登录回调。
   *
   * <p>用授权码换取访问令牌，获取社交用户信息，查找是否已有绑定记录。
   * 若已绑定则返回登录结果（含 accessToken/refreshToken），否则仅返回社交用户信息供前端展示绑定确认。
   *
   * @param platform 平台标识
   * @param code 授权码（平台回调携带）
   * @param state 状态码（用于 CSRF 校验）
   * @return 社交登录结果 VO
   * @throws BusinessException 认证失败时抛出
   */
  public SocialLoginVO callback(String platform, String code, String state) {
    checkSocialAuthEnabled();
    SocialAuthProvider provider = getProviderOrThrow(platform);

    // 校验 state 防止 CSRF 攻击
    if (!validateState(state)) {
      log.warn("Social auth CSRF check failed: platform={}, state={}", platform, state);
      throw new BusinessException(UserInfoExceptionCode.SOCIAL_AUTH_CSRF_FAILED);
    }

    try {
      // 换取访问令牌（redirectUri 必须与 authorize 时保持一致）
      var token = provider.exchangeToken(code, getRedirectUri(platform));
      // 获取社交用户信息
      SocialUserInfo userInfo = provider.getUserInfo(token);

      log.info(
          "Social auth callback: platform={}, openId={}, nickname={}",
          platform,
          userInfo.openId(),
          userInfo.nickname());

      // 查找是否已有绑定
      Optional<SocialAccountVO> existingBinding =
          socialAccountRepository.findByPlatformAndOpenId(platform, userInfo.openId());

      SocialLoginVO result = new SocialLoginVO();
      result.setPlatform(platform);
      result.setTokenType("Bearer");

      SocialLoginVO.SocialUserInfoVO userInfoVO = new SocialLoginVO.SocialUserInfoVO();
      userInfoVO.setOpenId(userInfo.openId());
      userInfoVO.setNickname(userInfo.nickname());
      userInfoVO.setAvatar(userInfo.avatar());
      userInfoVO.setEmail(userInfo.email());
      result.setSocialUserInfo(userInfoVO);

      if (existingBinding.isPresent()) {
        // 已绑定 —— 返回登录结果（此处仅填充平台信息，Token 签发由 AuthService 完成）
        result.setAccessToken("EXISTING_USER");
        log.info("Social login matched existing binding: platform={}, userId={}",
            platform, existingBinding.get().getUserId());
      } else {
        // 未绑定 —— 返回社交信息供前端展示绑定确认
        log.info("Social login no binding found: platform={}, openId={}",
            platform, userInfo.openId());
      }

      return result;
    } catch (SocialAuthException e) {
      log.warn("Social auth failed: platform={}, message={}", platform, e.getMessage());
      throw new BusinessException(UserInfoExceptionCode.SOCIAL_AUTH_FAILED);
    }
  }

  /**
   * 绑定社交账号到当前用户。
   *
   * <p>将社交账号与当前登录用户关联，保存绑定记录。每个用户每平台仅允许一个绑定。
   *
   * @param userId 当前用户 ID
   * @param platform 平台标识
   * @param openId 平台用户唯一标识
   * @param userInfo 社交用户信息
   * @throws BusinessException 该平台已绑定时抛出
   */
  public void bind(String userId, String platform, String openId, SocialUserInfo userInfo) {
    checkSocialAuthEnabled();

    // 检查是否已绑定
    socialAccountRepository
        .findByUserIdAndPlatform(userId, platform)
        .ifPresent(
            existing -> {
              throw new BusinessException(UserInfoExceptionCode.SOCIAL_BIND_EXISTS);
            });

    // 构建 DTO
    SocialAccountDTO dto = new SocialAccountDTO();
    dto.setUserId(userId);
    dto.setPlatform(platform);
    dto.setOpenId(openId);
    dto.setUnionId(userInfo.unionId());
    dto.setNickname(userInfo.nickname());
    dto.setAvatarUrl(userInfo.avatar());
    dto.setAccessToken(null);
    dto.setRefreshToken(null);
    dto.setExpiresAt(null);

    socialAccountRepository.save(dto);
    log.info("Social account bound: userId={}, platform={}, openId={}", userId, platform, openId);
  }

  /**
   * 解绑当前用户的社交账号。
   *
   * @param userId 当前用户 ID
   * @param platform 平台标识
   * @throws BusinessException 未绑定时抛出
   */
  public void unbind(String userId, String platform) {
    checkSocialAuthEnabled();

    // 检查是否存在绑定
    socialAccountRepository
        .findByUserIdAndPlatform(userId, platform)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.SOCIAL_ACCOUNT_NOT_BOUND));

    socialAccountRepository.deleteByUserIdAndPlatform(userId, platform);
    log.info("Social account unbound: userId={}, platform={}", userId, platform);
  }

  /**
   * 查询当前用户的社交账号绑定列表。
   *
   * @param userId 用户 ID
   * @return 社交账号绑定 VO 列表
   */
  public List<SocialAccountVO> listBindings(String userId) {
    return socialAccountRepository.listByUserId(userId);
  }

  /**
   * 校验社交认证全局开关。
   *
   * @throws BusinessException 社交认证未开启时抛出
   */
  private void checkSocialAuthEnabled() {
    if (!socialAuthProperties.isEnabled()) {
      throw new BusinessException(UserInfoExceptionCode.SOCIAL_AUTH_DISABLED);
    }
  }

  /**
   * 获取平台 Provider，不存在则抛出异常。
   *
   * @param platform 平台标识
   * @return 社交认证提供者
   * @throws BusinessException 平台不支持时抛出
   */
  private SocialAuthProvider getProviderOrThrow(String platform) {
    SocialAuthProvider provider = socialAuthProviderMap.get(platform.toUpperCase());
    if (provider == null) {
      throw new BusinessException(UserInfoExceptionCode.SOCIAL_PLATFORM_NOT_SUPPORTED);
    }
    return provider;
  }

  /**
   * 获取平台回调地址。
   *
   * @param platform 平台标识
   * @return 回调地址，未配置返回 null
   */
  private String getRedirectUri(String platform) {
    SocialAuthProperties.ProviderConfig config = socialAuthProperties.getProvider(platform);
    return config != null ? config.getRedirectUri() : null;
  }

  /**
   * 生成防 CSRF 的随机状态码，并存入 Redis 供回调校验。
   *
   * <p>state 值格式为 {@code social:state:{random}}，Redis TTL 10 分钟。
   * 回调时通过 {@link #validateState(String)} 校验并一次性消费。
   *
   * @return 随机状态码
   */
  private String generateState() {
    String state = UUID.randomUUID().toString().replace("-", "");
    try {
      redisStringOps.set(
          STATE_KEY_PREFIX + state,
          "1",
          STATE_TTL_SECONDS);
    } catch (Exception e) {
      log.warn("Failed to store state in Redis: state={}, error={}", state, e.getMessage(), e);
    }
    return state;
  }

  /**
   * 校验并消费 state（一次性）。
   *
   * <p>从 Redis 中删除 state，若删除成功表示 state 有效且未被使用过。
   *
   * @param state 回调携带的状态码
   * @return true 表示 state 有效；false 表示无效或已消费
   */
  private boolean validateState(String state) {
    if (state == null || state.isBlank()) {
      return false;
    }
    try {
      String key = STATE_KEY_PREFIX + state;
      String value = redisStringOps.get(key, String.class);
      if (value != null) {
        redisStringOps.del(key);
        return true;
      }
      log.warn("State validation failed: state not found or already consumed, state={}", state);
      return false;
    } catch (Exception e) {
      log.warn("Failed to validate state from Redis: state={}, error={}", state, e.getMessage(), e);
      return false;
    }
  }
}
