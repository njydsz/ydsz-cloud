package com.njydsz.userinfo.server.event;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.userinfo.domain.event.UserDomainEvent;
import com.njydsz.userinfo.domain.event.UserDomainEventType;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.infra.entity.DepartmentDO;
import com.njydsz.userinfo.infra.entity.RoleDO;
import com.njydsz.userinfo.infra.entity.UserAccountDO;

/**
 * 用户模块领域事件发布器。
 *
 * <p>统一封装 {@link UserDomainEvent} 的创建与发布，通过 common-event 的 {@link DomainEventPublisher} 门面投递事件。 业务服务在涉及用户/角色/部门变更时调用此组件。
 *
 * <p><b>事件类型枚举：</b>使用 {@link UserDomainEventType} 替代硬编码字符串，提供类型安全。
 *
 * <p><b>DDD 合规：</b>同时支持 {@link UserAccountDO}（infra 层）和 {@link UserAccountVO}（domain 层），新代码应优先使用 VO 版本。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class UserDomainEventPublisher {

  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  public UserDomainEventPublisher(ObjectProvider<DomainEventPublisher> eventPublisherProvider) {
    this.eventPublisherProvider = eventPublisherProvider;
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
   * 发布用户创建事件（DO 版本，兼容已有代码）。
   *
   * @param user 新建的用户实体
   * @deprecated 使用 {@link #publishUserCreated(UserAccountVO)} 替代
   */
  @Deprecated
  public void publishUserCreated(UserAccountDO user) {
    if (user == null) {
      return;
    }
    publish(
        UserDomainEventType.USER_CREATED,
        user.getId(),
        "USER",
        UserDomainEvent.of(
            UserDomainEventType.USER_CREATED,
            user.getId(),
            Map.of("username", user.getUsername(), "realName", orEmpty(user.getRealName()))));
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
   * 发布用户更新事件（DO 版本，兼容已有代码）。
   *
   * @param user 更新后的用户实体
   * @deprecated 使用 {@link #publishUserUpdated(UserAccountVO)} 替代
   */
  @Deprecated
  public void publishUserUpdated(UserAccountDO user) {
    if (user == null) {
      return;
    }
    publish(
        UserDomainEventType.USER_UPDATED,
        user.getId(),
        "USER",
        UserDomainEvent.of(
            UserDomainEventType.USER_UPDATED,
            user.getId(),
            Map.of("username", user.getUsername(), "status", String.valueOf(user.getStatus()))));
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

  /**
   * 发布用户登录事件。
   *
   * @param userId 登录用户 ID
   */
  public void publishUserLogin(String userId) {
    publish(
        UserDomainEventType.USER_LOGIN,
        userId,
        "USER",
        UserDomainEvent.of(UserDomainEventType.USER_LOGIN, userId, Map.of()));
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
