package com.njydsz.userinfo.server.event;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.userinfo.domain.event.UserDomainEvent;
import com.njydsz.userinfo.domain.event.UserDomainEventType;
import com.njydsz.userinfo.domain.event.auth.AccountBannedEvent;
import com.njydsz.userinfo.domain.event.auth.AccountLockedEvent;
import com.njydsz.userinfo.domain.event.auth.AccountUnbannedEvent;
import com.njydsz.userinfo.domain.event.auth.AccountUnlockedEvent;
import com.njydsz.userinfo.domain.event.auth.LoginFailedEvent;
import com.njydsz.userinfo.domain.event.auth.LoginSuccessEvent;
import com.njydsz.userinfo.domain.event.auth.LogoutEvent;
import com.njydsz.userinfo.domain.event.auth.MfaFailedEvent;
import com.njydsz.userinfo.domain.event.auth.MfaTriggeredEvent;
import com.njydsz.userinfo.domain.event.auth.MfaVerifiedEvent;
import com.njydsz.userinfo.domain.event.auth.PasswordChangedEvent;
import com.njydsz.userinfo.domain.event.auth.SessionEvictedEvent;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.entity.DepartmentDO;
import com.njydsz.userinfo.infra.entity.RoleDO;

/**
 * 用户模块领域事件发布器。
 *
 * <p>统一封装 {@link UserDomainEvent} 的创建与发布，通过 common-event 的 {@link DomainEventPublisher} 门面投递事件。 业务服务在涉及用户/角色/部门变更时调用此组件。
 *
 * <p><b>事件类型枚举：</b>使用 {@link UserDomainEventType} 替代硬编码字符串，提供类型安全。
 *
 * <p><b>DDD 合规：</b>仅使用 {@link UserAccountVO}（domain 层）发布事件，不依赖 infra 层实体。
 *
 * <p><b>认证事件（v1.6.0+）：</b>新增的认证事件发布方法通过 {@link UserAuthEventDispatcher} 分发，替代旧的 {@code publishUserLogin} 等字符串事件。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class UserDomainEventPublisher {

  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;
  private final UserAuthEventDispatcher authEventDispatcher;

  public UserDomainEventPublisher(
      ObjectProvider<DomainEventPublisher> eventPublisherProvider,
      UserAuthEventDispatcher authEventDispatcher) {
    this.eventPublisherProvider = eventPublisherProvider;
    this.authEventDispatcher = authEventDispatcher;
  }

  /**
   * 发布用户创建事件（VO 版本，推荐新代码使用）。
   *
   * @param userVO 新建的用户领域 VO
   */
  public void publishUserCreated(UserAccountVO userVO) {
    if (userVO == null) {
      return;
    }
    publish(
        UserDomainEventType.USER_CREATED,
        userVO.getId(),
        "USER",
        UserDomainEvent.of(
            UserDomainEventType.USER_CREATED,
            userVO.getId(),
            Map.of("username", userVO.getUsername(), "realName", orEmpty(userVO.getRealName()))));
  }

  /**
   * 发布用户更新事件（VO 版本，推荐新代码使用）。
   *
   * @param userVO 更新后的用户领域 VO
   */
  public void publishUserUpdated(UserAccountVO userVO) {
    if (userVO == null) {
      return;
    }
    publish(
        UserDomainEventType.USER_UPDATED,
        userVO.getId(),
        "USER",
        UserDomainEvent.of(
            UserDomainEventType.USER_UPDATED,
            userVO.getId(),
            Map.of("username", userVO.getUsername(), "status", String.valueOf(userVO.getStatus()))));
  }

  /**
   * 发布用户删除事件。
   *
   * @param userId 被删除的用户 ID
   * @param username 被删除的用户名
   */
  public void publishUserDeleted(String userId, String username) {
    publish(
        UserDomainEventType.USER_DELETED,
        userId,
        "USER",
        UserDomainEvent.of(UserDomainEventType.USER_DELETED, userId, Map.of("username", username)));
  }

  // ==================== 认证事件（v1.6.0+，通过 UserAuthEventDispatcher 分发） ====================

  /**
   * 发布登录成功事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param sourceIp 登录来源 IP
   * @param userAgent 浏览器 User-Agent
   * @param deviceType 设备类型
   */
  public void publishLoginSuccess(
      String userId, String username, String sourceIp, String userAgent, String deviceType) {
    authEventDispatcher.publish(
        new LoginSuccessEvent(userId, username, LocalDateTime.now(), sourceIp, userAgent, deviceType));
  }

  /**
   * 发布登录失败事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param sourceIp 登录来源 IP
   * @param reason 失败原因
   * @param failCount 累计失败次数
   */
  public void publishLoginFailed(
      String userId, String username, String sourceIp, String reason, int failCount) {
    authEventDispatcher.publish(
        new LoginFailedEvent(userId, username, LocalDateTime.now(), sourceIp, reason, failCount));
  }

  /**
   * 发布注销事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param sourceIp 注销来源 IP
   * @param sessionDuration 会话持续时长（毫秒）
   */
  public void publishLogout(String userId, String username, String sourceIp, long sessionDuration) {
    authEventDispatcher.publish(
        new LogoutEvent(userId, username, LocalDateTime.now(), sourceIp, sessionDuration));
  }

  /**
   * 发布 MFA 触发事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param mfaType MFA 类型
   */
  public void publishMfaTriggered(String userId, String username, String mfaType) {
    authEventDispatcher.publish(
        new MfaTriggeredEvent(userId, username, LocalDateTime.now(), mfaType));
  }

  /**
   * 发布 MFA 验证成功事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param mfaType MFA 类型
   */
  public void publishMfaVerified(String userId, String username, String mfaType) {
    authEventDispatcher.publish(
        new MfaVerifiedEvent(userId, username, LocalDateTime.now(), mfaType));
  }

  /**
   * 发布 MFA 验证失败事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param mfaType MFA 类型
   * @param reason 失败原因
   */
  public void publishMfaFailed(String userId, String username, String mfaType, String reason) {
    authEventDispatcher.publish(
        new MfaFailedEvent(userId, username, LocalDateTime.now(), mfaType, reason));
  }

  /**
   * 发布账号锁定事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param lockDuration 锁定持续时长（分钟），-1 表示永久锁定
   * @param reason 锁定原因
   */
  public void publishAccountLocked(String userId, String username, long lockDuration, String reason) {
    authEventDispatcher.publish(
        new AccountLockedEvent(userId, username, LocalDateTime.now(), lockDuration, reason));
  }

  /**
   * 发布账号解锁事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param unlockedBy 解锁操作者
   */
  public void publishAccountUnlocked(String userId, String username, String unlockedBy) {
    authEventDispatcher.publish(
        new AccountUnlockedEvent(userId, username, LocalDateTime.now(), unlockedBy));
  }

  /**
   * 发布会话驱逐事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param evictedBy 驱逐操作者
   * @param reason 驱逐原因
   */
  public void publishSessionEvicted(
      String userId, String username, String evictedBy, String reason) {
    authEventDispatcher.publish(
        new SessionEvictedEvent(userId, username, LocalDateTime.now(), evictedBy, reason));
  }

  /**
   * 发布密码修改事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param changedBy 操作者
   */
  public void publishPasswordChanged(String userId, String username, String changedBy) {
    authEventDispatcher.publish(
        new PasswordChangedEvent(userId, username, LocalDateTime.now(), changedBy));
  }

  /**
   * 发布账号封禁事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param banType 封禁类型
   * @param reason 封禁原因
   * @param bannedBy 封禁操作者
   */
  public void publishAccountBanned(
      String userId, String username, String banType, String reason, String bannedBy) {
    authEventDispatcher.publish(
        new AccountBannedEvent(userId, username, LocalDateTime.now(), banType, reason, bannedBy));
  }

  /**
   * 发布账号解封事件。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param unbannedBy 解封操作者
   */
  public void publishAccountUnbanned(String userId, String username, String unbannedBy) {
    authEventDispatcher.publish(
        new AccountUnbannedEvent(userId, username, LocalDateTime.now(), unbannedBy));
  }

  /**
   * 发布角色变更事件（用户角色分配变更）。
   *
   * @param userId 关联的用户 ID
   * @param roleCount 变更的角色数量
   */
  public void publishRoleChanged(String userId, int roleCount) {
    publish(
        UserDomainEventType.ROLE_CHANGED,
        userId,
        "USER",
        UserDomainEvent.of(
            UserDomainEventType.ROLE_CHANGED,
            userId,
            Map.of("action", "ASSIGN", "roleCount", roleCount)));
  }

  /**
   * 发布角色实体变更事件。
   *
   * @param RoleDO 角色实体
   * @param action 操作类型（CREATED / UPDATED / DELETED）
   */
  public void publishRoleEntityChanged(RoleDO RoleDO, String action) {
    if (RoleDO == null) {
      return;
    }
    publish(
        UserDomainEventType.ROLE_CHANGED,
        RoleDO.getId(),
        "RoleDO",
        new UserDomainEvent(
            UserDomainEventType.ROLE_CHANGED,
            RoleDO.getId(),
            "RoleDO",
            Map.of("roleId", RoleDO.getId(), "roleCode", orEmpty(RoleDO.getRoleCode()), "action", action)));
  }

  /**
   * 发布角色实体变更事件（VO 版本，推荐新代码使用）。
   *
   * @param roleVO 角色 VO
   * @param action 操作类型（CREATED / UPDATED / DELETED）
   */
  public void publishRoleEntityChanged(RoleVO roleVO, String action) {
    if (roleVO == null) {
      return;
    }
    publish(
        UserDomainEventType.ROLE_CHANGED,
        roleVO.getId(),
        "RoleDO",
        new UserDomainEvent(
            UserDomainEventType.ROLE_CHANGED,
            roleVO.getId(),
            "RoleDO",
            Map.of("roleId", roleVO.getId(), "roleCode", orEmpty(roleVO.getRoleCode()), "action", action)));
  }

  /**
   * 发布部门变更事件（VO 版本，推荐新代码使用）。
   *
   * @param deptVO 部门 VO
   * @param action 操作类型（CREATED / UPDATED / DELETED）
   */
  public void publishDepartmentChanged(DepartmentVO deptVO, String action) {
    if (deptVO == null) {
      return;
    }
    publish(
        UserDomainEventType.ORG_STRUCTURE_CHANGED,
        deptVO.getId(),
        "DepartmentDO",
        new UserDomainEvent(
            UserDomainEventType.ORG_STRUCTURE_CHANGED,
            deptVO.getId(),
            "DepartmentDO",
            Map.of(
                "deptId",
                deptVO.getId(),
                "deptCode",
                orEmpty(deptVO.getDeptCode()),
                "deptName",
                orEmpty(deptVO.getDeptName()),
                "action",
                action)));
  }

  /**
   * 发布部门变更事件。
   *
   * @param dept 部门实体
   * @param action 操作类型（CREATED / UPDATED / DELETED）
   */
  public void publishDepartmentChanged(DepartmentDO dept, String action) {
    if (dept == null) {
      return;
    }
    publish(
        UserDomainEventType.ORG_STRUCTURE_CHANGED,
        dept.getId(),
        "DepartmentDO",
        new UserDomainEvent(
            UserDomainEventType.ORG_STRUCTURE_CHANGED,
            dept.getId(),
            "DepartmentDO",
            Map.of(
                "deptId",
                dept.getId(),
                "deptCode",
                orEmpty(dept.getDeptCode()),
                "deptName",
                orEmpty(dept.getDeptName()),
                "action",
                action)));
  }

  // ==================== 内部方法 ====================

  private void publish(
      UserDomainEventType eventType, String aggregateId, String aggregateType, DomainEvent event) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    publisher.publish(event);
    log.debug("Domain event published: type={}, aggregateId={}", eventType.getCode(), aggregateId);
  }

  private static String orEmpty(String value) {
    return value != null ? value : "";
  }
}
