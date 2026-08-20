package com.njydsz.userinfo.server.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.enums.BanType;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.enums.UserLifecycleStatusEnum;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;
import com.njydsz.userinfo.server.service.LoginAttemptContext;
import com.njydsz.userinfo.server.service.LoginHistoryService;

/**
 * 账号状态守卫。
 *
 * <p>负责登录前查询用户并校验账号状态（不存在/禁用/锁定），失败时记录登录历史与指标。 从 {@link AuthServiceImpl}
 * 拆分（P0-5），聚焦「用户查询 + 状态校验」单一职责。
 *
 * <p><b>DDD 合规：</b>通过 {@link UserAccountRepository} 访问数据，不直接依赖 Mapper。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see UserAccountCredentialVO 用户认证凭据 VO
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountStatusGuard {

  private final UserAccountRepository userAccountRepository;
  private final LoginHistoryService loginHistoryService;
  private final UserInfoMetrics userInfoMetrics;

  /**
   * 查询用户并校验账号状态与锁定状态。
   *
   * <p>通过 {@link UserAccountRepository#findCredentialByUsername} 获取用户认证凭据，校验账号状态（不存在/生命周期状态/锁定）。
   *
   * <p>使用 {@link UserLifecycleStatusEnum} 进行状态校验，覆盖所有生命周期状态：
   *
   * <ul>
   *   <li>PENDING → 抛出 {@link UserInfoExceptionCode#USER_NOT_ACTIVATED}
   *   <li>SUSPENDED → 抛出 {@link UserInfoExceptionCode#USER_SUSPENDED}
   *   <li>RESIGNED → 抛出 {@link UserInfoExceptionCode#USER_RESIGNED}
   *   <li>DISABLED → 抛出 {@link UserInfoExceptionCode#USER_DISABLED}
   * </ul>
   *
   * @param username 登录用户名
   * @param loginIp 登录来源 IP
   * @param userAgent 用户代理
   * @return 有效的用户账号凭据 VO
   * @throws BusinessException 用户不存在、未激活、已暂停、已离职、已禁用或已锁定时抛出
   */
  public UserAccountCredentialVO findValidUser(String username, String loginIp, String userAgent) {
    Optional<UserAccountCredentialVO> credentialOpt = userAccountRepository.findCredentialByUsername(username);

    if (credentialOpt.isEmpty()) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(null, username, loginIp), "FAILED", "USER_NOT_FOUND", userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
    }

    UserAccountCredentialVO credential = credentialOpt.get();

    UserLifecycleStatusEnum lifecycleStatus = resolveLifecycleStatus(credential);
    if (lifecycleStatus != null && !lifecycleStatus.canLogin()) {
      switch (lifecycleStatus) {
        case PENDING -> {
          recordLoginFailure(credential, username, loginIp, userAgent, "USER_NOT_ACTIVATED");
          throw new BusinessException(UserInfoExceptionCode.USER_NOT_ACTIVATED);
        }
        case SUSPENDED -> {
          recordLoginFailure(credential, username, loginIp, userAgent, "USER_SUSPENDED");
          throw new BusinessException(UserInfoExceptionCode.USER_SUSPENDED);
        }
        case RESIGNED -> {
          recordLoginFailure(credential, username, loginIp, userAgent, "USER_RESIGNED");
          throw new BusinessException(UserInfoExceptionCode.USER_RESIGNED);
        }
        case DISABLED -> {
          recordLoginFailure(credential, username, loginIp, userAgent, "USER_DISABLED");
          throw new BusinessException(UserInfoExceptionCode.USER_DISABLED);
        }
        default -> {
          // ENABLED falls through to lock check below
        }
      }
    }

    // 封禁状态检查：在生命周期状态校验之后、密码校验之前
    String banType = credential.getBanType();
    if (banType != null && !banType.isBlank()) {
      try {
        BanType type = BanType.valueOf(banType.trim().toUpperCase());
        if (type == BanType.PERMANENT) {
          recordLoginFailure(credential, username, loginIp, userAgent, "USER_BANNED_PERMANENT");
          throw new BusinessException(UserInfoExceptionCode.USER_BANNED_PERMANENT);
        }
        // TEMPORARY: 懒检查是否过期
        LocalDateTime banExpireAt = credential.getBanExpireAt();
        if (banExpireAt == null || !banExpireAt.isAfter(LocalDateTime.now())) {
          // 临时封禁已过期或时间异常，自动解除（不写回 DB，仅内存判断）
          log.debug("Temporary ban expired for user: {}, allowing login", username);
        } else {
          recordLoginFailure(credential, username, loginIp, userAgent, "USER_BANNED");
          throw new BusinessException(UserInfoExceptionCode.USER_BANNED);
        }
      } catch (IllegalArgumentException e) {
        // 无法解析的 banType 值，不阻止登录（防御性处理）
        log.warn("Unknown banType for user {}: {}", username, banType);
      }
    }

    if (credential.isLocked()) {
      recordLoginFailure(credential, username, loginIp, userAgent, "ACCOUNT_LOCKED");
      throw new BusinessException(UserInfoExceptionCode.ACCOUNT_LOCKED);
    }

    return credential;
  }

  /**
   * 解析用户生命周期状态。
   *
   * @param credential 用户认证凭据 VO
   * @return 生命周期状态枚举，无法解析时返回 null
   */
  private UserLifecycleStatusEnum resolveLifecycleStatus(UserAccountCredentialVO credential) {
    if (credential.getStatus() == null) {
      return null;
    }
    return UserLifecycleStatusEnum.parse(String.valueOf(credential.getStatus()));
  }

  /**
   * 记录登录失败（生命周期状态相关）。
   *
   * @param credential 用户认证凭据 VO
   * @param username 登录用户名
   * @param loginIp 登录来源 IP
   * @param userAgent 用户代理
   * @param reason 失败原因标识
   */
  private void recordLoginFailure(
      UserAccountCredentialVO credential,
      String username,
      String loginIp,
      String userAgent,
      String reason) {
    loginHistoryService.recordLoginAttempt(
        new LoginAttemptContext(credential.getId(), username, loginIp), "FAILED", reason, userAgent);
    userInfoMetrics.recordLoginFail();
  }

}
