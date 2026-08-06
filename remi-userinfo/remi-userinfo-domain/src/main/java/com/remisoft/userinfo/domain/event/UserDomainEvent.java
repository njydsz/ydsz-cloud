package com.remisoft.userinfo.domain.event;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import com.remisoft.common.event.api.DomainEvent;
import com.remisoft.common.event.api.ModuleEventTypes;

import lombok.Getter;
import com.remisoft.common.util.id.IdGenerator;

/**
 * 用户域领域事件。
 *
 * <p>封装用户模块的用户/角色/组织变更事件，继承 {@link DomainEvent}，
 * 事件类型常量统一取自 {@link ModuleEventTypes}（USER_CREATED / USER_UPDATED / USER_DELETED /
 * ROLE_CHANGED / DEPARTMENT_CHANGED）。
 *
 * <p><b>发布方式：</b>
 * <pre>{@code
 * applicationEventPublisher.publishEvent(
 *     UserDomainEvent.of(ModuleEventTypes.USER_CREATED, userId, Map.of("username", "admin")));
 * }</pre>
 *
 * <p><b>消费方式（推荐事务提交后）：</b>
 * <pre>{@code
 * &#64;TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * public void onUserCreated(UserDomainEvent event) { ... }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Getter
public class UserDomainEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 构造用户域事件。
     *
     * @param eventType    事件类型（取自 {@link ModuleEventTypes}）
     * @param userId       用户 ID（映射为 aggregateId）
     * @param aggregateType 聚合根类型（USER / ROLE / DEPARTMENT）
     * @param metadata     扩展元数据
     */
    public UserDomainEvent(String eventType, String userId, String aggregateType,
                           Map<String, Object> metadata) {
        super(IdGenerator.nextIdStr(), LocalDateTime.now(), eventType,
                userId, aggregateType,
                metadata != null ? metadata : Collections.emptyMap());
    }

    /**
     * 便捷工厂：创建用户域事件。
     *
     * @param eventType  事件类型（取自 {@link ModuleEventTypes}）
     * @param userId     用户 ID
     * @param metadata   扩展元数据
     * @return 用户域事件实例
     */
    public static UserDomainEvent of(String eventType, String userId, Map<String, Object> metadata) {
        return new UserDomainEvent(eventType, userId, "USER", metadata);
    }

    /**
     * 获取用户 ID（即 aggregateId，语义别名）。
     *
     * @return 用户 ID
     */
    public String getUserId() {
        return getAggregateId();
    }
}
