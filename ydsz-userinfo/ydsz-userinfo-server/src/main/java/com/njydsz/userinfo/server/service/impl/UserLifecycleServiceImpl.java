package com.njydsz.userinfo.server.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.enums.UserLifecycleStatusEnum;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;
import com.njydsz.userinfo.server.service.UserLifecycleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户生命周期状态机服务实现（P2-3）。
 *
 * <p>提供 PENDING → ENABLED → SUSPENDED/DISABLED → RESIGNED 完整状态流转能力。
 * 所有流转均通过 {@link UserLifecycleStatusEnum#canTransitTo} 进行前置校验，
 * 终态 RESIGNED 不允许任何流出流转。
 *
 * <p><b>幂等性：</b>流转到当前状态时不报错，直接返回（兼容前端重复提交）。
 *
 * <b>事件发布：</b>每次成功流转后发布 {@code USER_STATUS_CHANGED} 领域事件，
 * 供审计日志和下游订阅方消费。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLifecycleServiceImpl implements UserLifecycleService {

  private final UserAccountRepository userAccountRepository;
  private final UserDomainEventPublisher eventPublisher;

  @Override
  public UserLifecycleStatusEnum activate(String userId) {
    return transition(userId, UserLifecycleStatusEnum.ENABLED);
  }

  @Override
  public UserLifecycleStatusEnum suspend(String userId) {
    return transition(userId, UserLifecycleStatusEnum.SUSPENDED);
  }

  @Override
  public UserLifecycleStatusEnum resume(String userId) {
    return transition(userId, UserLifecycleStatusEnum.ENABLED);
  }

  @Override
  public UserLifecycleStatusEnum disable(String userId) {
    return transition(userId, UserLifecycleStatusEnum.DISABLED);
  }

  @Override
  public UserLifecycleStatusEnum enable(String userId) {
    return transition(userId, UserLifecycleStatusEnum.ENABLED);
  }

  @Override
  public UserLifecycleStatusEnum resign(String userId) {
    return transition(userId, UserLifecycleStatusEnum.RESIGNED);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public UserLifecycleStatusEnum transition(String userId, UserLifecycleStatusEnum target) {
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.PARAM_INVALID);
    }
    if (target == null) {
      throw new BusinessException(UserInfoExceptionCode.PARAM_INVALID);
    }

    UserAccountVO user = userAccountRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("生命周期流转失败[用户不存在]: userId={}, target={}", userId, target);
          return new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
        });

    // 解析当前生命周期状态
    UserLifecycleStatusEnum current = resolveCurrentStatus(user);

    // 幂等：已经在目标状态，直接返回
    if (current == target) {
      log.debug("生命周期流转幂等跳过: userId={}, status={}", userId, target);
      return target;
    }

    // 校验流转合法性
    if (!current.canTransitTo(target)) {
      log.warn("非法状态流转: userId={}, current={}, target={}", userId, current, target);
      throw new BusinessException(UserInfoExceptionCode.LIFECYCLE_TRANSITION_INVALID);
    }

    // 执行流转
    int affected = userAccountRepository.updateLifecycleStatus(userId, target);
    if (affected == 0) {
      log.warn("生命周期流转失败[更新影响行数为 0]: userId={}, target={}", userId, target);
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }

    log.info("生命周期流转成功: userId={}, {} → {}", userId, current, target);

    // 发布领域事件（终态 RESIGNED 额外发布会话驱逐）
    eventPublisher.publishUserUpdated(user);

    return target;
  }

  /**
   * 解析用户当前生命周期状态。
   *
   * <p>{@link UserAccountVO#getStatus()} 为 Integer（兼容旧格式 0/1），
   * 新状态使用枚举字面量字符串。通过 {@link UserLifecycleStatusEnum#parse} 兼容两种格式。
   *
   * @param user 用户 VO
   * @return 当前生命周期状态，无法解析时默认 DISABLED
   */
  private UserLifecycleStatusEnum resolveCurrentStatus(UserAccountVO user) {
    // 优先尝试从枚举名格式解析（PENDING/SUSPENDED/RESIGNED）
    if (user.getStatus() == null) {
      return UserLifecycleStatusEnum.PENDING;
    }
    // status 字段存储为 Integer：1=ENABLED, 0=DISABLED
    if (user.getStatus() == 1) {
      return UserLifecycleStatusEnum.ENABLED;
    }
    if (user.getStatus() == 0) {
      return UserLifecycleStatusEnum.DISABLED;
    }
    // 其他情况尝试解析字符串格式
    UserLifecycleStatusEnum parsed = UserLifecycleStatusEnum.parse(String.valueOf(user.getStatus()));
    return parsed != null ? parsed : UserLifecycleStatusEnum.DISABLED;
  }
}
