package com.njydsz.userinfo.server.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
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
   * <p>通过 {@link UserAccountRepository#findCredentialByUsername} 获取用户认证凭据，校验账号状态（不存在/禁用/锁定）。
   *
   * @param username 登录用户名
   * @param loginIp 登录来源 IP
   * @param userAgent 用户代理
   * @return 有效的用户账号实体（由领域凭据 VO 转换而来，兼容现有调用方）
   * @throws BusinessException 用户不存在、已禁用或已锁定时抛出
   */
  public UserAccountDO findValidUser(String username, String loginIp, String userAgent) {
    Optional<UserAccountCredentialVO> credentialOpt = userAccountRepository.findCredentialByUsername(username);

    if (credentialOpt.isEmpty()) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(null, username, loginIp), "FAILED", "USER_NOT_FOUND", userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
    }

    UserAccountCredentialVO credential = credentialOpt.get();

    if (isDisabled(credential)) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(credential.getId(), username, loginIp),
          "FAILED",
          "USER_DISABLED",
          userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.USER_DISABLED);
    }

    if (credential.isLocked()) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(credential.getId(), username, loginIp),
          "FAILED",
          "ACCOUNT_LOCKED",
          userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.ACCOUNT_LOCKED);
    }

    return toUserAccountDO(credential);
  }

  /**
   * 判断账号是否被禁用。
   *
   * @param credential 用户认证凭据 VO
   * @return true 表示账号被禁用
   */
  private boolean isDisabled(UserAccountCredentialVO credential) {
    if (credential.getStatus() == null) {
      return false;
    }
    EnableStatusEnum statusEnum = EnableStatusEnum.parse(String.valueOf(credential.getStatus()));
    return statusEnum == EnableStatusEnum.DISABLED;
  }

  /**
   * 将领域凭据 VO 转换为 DO（兼容现有调用方）。
   *
   * <p><b>注意：</b>此方法仅为迁移期兼容，新代码应直接使用 {@link UserAccountCredentialVO}。
   *
   * @param credential 用户认证凭据 VO
   * @return 用户账号 DO（仅包含必要字段）
   */
  private UserAccountDO toUserAccountDO(UserAccountCredentialVO credential) {
    UserAccountDO user = new UserAccountDO();
    user.setId(credential.getId());
    user.setUsername(credential.getUsername());
    user.setPassword(credential.getPassword());
    user.setTenantId(credential.getTenantId());
    user.setStatus(credential.getStatus());
    user.setLoginFailCount(credential.getLoginFailCount());
    user.setLockedUntil(credential.getLockedUntil());
    return user;
  }
}
