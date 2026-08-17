package com.njydsz.userinfo.server.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;
import com.njydsz.userinfo.server.service.LoginAttemptContext;
import com.njydsz.userinfo.server.service.LoginHistoryService;

/**
 * 账号状态守卫。
 *
 * <p>负责登录前查询用户并校验账号状态（不存在/禁用/锁定），失败时记录登录历史与指标。 从 {@link AuthServiceImpl}
 * 拆分（P0-5），聚焦「用户查询 + 状态校验」单一职责。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see UserAccountDO 用户账号实体
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountStatusGuard {

  private final UserAccountMapper userAccountMapper;
  private final LoginHistoryService loginHistoryService;
  private final UserInfoMetrics userInfoMetrics;

  /**
   * 查询用户并校验账号状态与锁定状态。
   *
   * @param username 登录用户名
   * @param loginIp 登录来源 IP
   * @param userAgent 用户代理
   * @return 有效的用户账号实体
   * @throws BusinessException 用户不存在、已禁用或已锁定时抛出
   */
  public UserAccountDO findValidUser(String username, String loginIp, String userAgent) {
    LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserAccountDO::getUsername, username);
    UserAccountDO user = userAccountMapper.selectOne(wrapper);

    if (user == null) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(null, username, loginIp), "FAILED", "USER_NOT_FOUND", userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.PASSWORD_INCORRECT);
    }

    if (user.getStatusEnum() == EnableStatusEnum.DISABLED) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(user.getId(), username, loginIp),
          "FAILED",
          "USER_DISABLED",
          userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.USER_DISABLED);
    }

    if (user.isLocked()) {
      loginHistoryService.recordLoginAttempt(
          new LoginAttemptContext(user.getId(), username, loginIp),
          "FAILED",
          "ACCOUNT_LOCKED",
          userAgent);
      userInfoMetrics.recordLoginFail();
      throw new BusinessException(UserInfoExceptionCode.ACCOUNT_LOCKED);
    }

    return user;
  }
}
