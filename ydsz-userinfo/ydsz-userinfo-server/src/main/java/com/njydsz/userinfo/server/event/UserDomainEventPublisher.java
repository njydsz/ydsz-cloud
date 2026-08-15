package com.njydsz.userinfo.server.event;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.ModuleEventTypes;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.event.UserDomainEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户模块领域事件发布器。
 *
 * <p>统一封装 UserDomainEvent 的创建与发布逻辑，通过 Outbox 模式保证事件投递的事务一致性。
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
 */
@Slf4j
@Component
public class UserDomainEventPublisher {

    private final ObjectProvider<OutboxService> outboxServiceProvider;

    public UserDomainEventPublisher(ObjectProvider<OutboxService> outboxServiceProvider) {
        this.outboxServiceProvider = outboxServiceProvider;
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
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", user.getUsername());
        metadata.put("realName", user.getRealName());
        publish(ModuleEventTypes.USER_CREATED, user.getId(), "USER", metadata);
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
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", user.getUsername());
        metadata.put("status", user.getStatus());
        publish(ModuleEventTypes.USER_UPDATED, user.getId(), "USER", metadata);
    }

    /**
     * 发布用户删除事件
     *
     * @param userId   被删除的用户 ID
     * @param username 被删除的用户名
     */
    public void publishUserDeleted(String userId, String username) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", username);
        publish(ModuleEventTypes.USER_DELETED, userId, "USER", metadata);
    }

    /**
     * 发布角色变更事件（角色分配/撤销/权限修改）
     *
     * @param userId 关联的用户 ID
     * @param roleIds 变更的角色 ID 列表大小信息
     */
    public void publishRoleChanged(String userId, int roleCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", "ROLE_CHANGED");
        metadata.put("roleCount", roleCount);
        publish(ModuleEventTypes.ROLE_CHANGED, userId, "USER", metadata);
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
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("roleId", role.getId());
        metadata.put("roleCode", role.getRoleCode());
        metadata.put("action", action);
        publish(ModuleEventTypes.ROLE_CHANGED, role.getId(), "ROLE", metadata);
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
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("deptId", dept.getId());
        metadata.put("deptCode", dept.getDeptCode());
        metadata.put("deptName", dept.getDeptName());
        metadata.put("action", action);
        publish(ModuleEventTypes.DEPARTMENT_CHANGED, dept.getId(), "DEPARTMENT", metadata);
    }

    /**
     * 内部方法：通过 Outbox 发布事件
     */
    private void publish(String eventType, String aggregateId,
                         String aggregateType, Map<String, Object> metadata) {
        DomainEvent event = new UserDomainEvent(eventType, aggregateId, aggregateType, metadata);
        OutboxService outbox = outboxServiceProvider.getIfAvailable();
        if (outbox != null) {
            outbox.appendToOutbox(event);
            log.debug("Domain event published via Outbox: type={}, aggregateId={}", eventType, aggregateId);
        } else {
            log.debug("OutboxService not available, skipping domain event: type={}, aggregateId={}",
                    eventType, aggregateId);
        }
    }
}
