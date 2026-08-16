package com.njydsz.userinfo.server.event;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.event.UserDomainEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户模块领域事件发布器。
 *
 * <p>统一封装 UserDomainEvent 的创建逻辑，通过 {@link DomainEventPublisher} 发布事件。
 * 业务服务在涉及用户/角色/部门变更时调用此组件发布对应领域事件。
 *
 * <p><b>事件列表：</b>
 * <ul>
 *   <li>USER_CREATED — 用户创建时</li>
 *   <li>USER_UPDATED — 用户更新时</li>
 *   <li>USER_DELETED — 用户删除时</li>
 *   <li>ROLE_CHANGED — 角色分配/变更时</li>
 *   <li>DEPARTMENT_CHANGED — 部门变更时</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.8.0 重构：内部改用 {@link DomainEventPublisher} 统一门面，消除重复代码
 */
@Slf4j
@Component
public class UserDomainEventPublisher {

    /** 统一领域事件发布门面 */
    private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

    public UserDomainEventPublisher(ObjectProvider<DomainEventPublisher> eventPublisherProvider) {
        this.eventPublisherProvider = eventPublisherProvider;
    }

    /**
     * 发布用户创建事件
     *
     * @param user 新建的用户实体
     */
    public void publishUserCreated(UserAccount user) {
        if (user == null) {
            return;
        }
        publish(DomainEventTypes.USER_CREATED, user.getId(), "USER",
                UserDomainEvent.of(DomainEventTypes.USER_CREATED, user.getId(),
                        java.util.Map.of("username", user.getUsername(), "realName", user.getRealName())));
    }

    /**
     * 发布用户更新事件
     *
     * @param user 更新后的用户实体
     */
    public void publishUserUpdated(UserAccount user) {
        if (user == null) {
            return;
        }
        publish(DomainEventTypes.USER_UPDATED, user.getId(), "USER",
                UserDomainEvent.of(DomainEventTypes.USER_UPDATED, user.getId(),
                        java.util.Map.of("username", user.getUsername(), "status", user.getStatus())));
    }

    /**
     * 发布用户删除事件
     *
     * @param userId   被删除的用户 ID
     * @param username 被删除的用户名
     */
    public void publishUserDeleted(String userId, String username) {
        publish(DomainEventTypes.USER_DELETED, userId, "USER",
                UserDomainEvent.of(DomainEventTypes.USER_DELETED, userId,
                        java.util.Map.of("username", username)));
    }

    /**
     * 发布角色变更事件（角色分配/撤销/权限修改）
     *
     * @param userId 关联的用户 ID
     * @param roleCount 变更的角色数量
     */
    public void publishRoleChanged(String userId, int roleCount) {
        publish(DomainEventTypes.ROLE_CHANGED, userId, "USER",
                UserDomainEvent.of(DomainEventTypes.ROLE_CHANGED, userId,
                        java.util.Map.of("action", "ROLE_CHANGED", "roleCount", roleCount)));
    }

    /**
     * 发布角色实体变更事件
     *
     * @param role   角色实体
     * @param action 操作类型（CREATED / UPDATED / DELETED）
     */
    public void publishRoleEntityChanged(Role role, String action) {
        if (role == null) {
            return;
        }
        publish(DomainEventTypes.ROLE_CHANGED, role.getId(), "ROLE",
                new UserDomainEvent(DomainEventTypes.ROLE_CHANGED, role.getId(), "ROLE",
                        java.util.Map.of("roleId", role.getId(), "roleCode", role.getRoleCode(), "action", action)));
    }

    /**
     * 发布部门变更事件
     *
     * @param dept   部门实体
     * @param action 操作类型（CREATED / UPDATED / DELETED）
     */
    public void publishDepartmentChanged(Department dept, String action) {
        if (dept == null) {
            return;
        }
        publish(DomainEventTypes.ORG_STRUCTURE_CHANGED, dept.getId(), "DEPARTMENT",
                new UserDomainEvent(DomainEventTypes.ORG_STRUCTURE_CHANGED, dept.getId(), "DEPARTMENT",
                        java.util.Map.of("deptId", dept.getId(), "deptCode", dept.getDeptCode(),
                                "deptName", dept.getDeptName(), "action", action)));
    }

    /**
     * 内部方法：通过统一门面发布领域事件
     *
     * @param eventType    事件类型
     * @param aggregateId  聚合根 ID
     * @param aggregateType 聚合根类型
     * @param event        领域事件实例
     */
    private void publish(String eventType, String aggregateId,
                         String aggregateType, DomainEvent event) {
        DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
        if (publisher != null) {
            publisher.publish(event);
            log.debug("Domain event published: type={}, aggregateId={}", eventType, aggregateId);
        }
    }
}
