package com.njydsz.project.domain.event;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.ModuleEventTypes;

import lombok.Getter;

/**
 * 项目域领域事件。
 *
 * <p>封装项目模块的立项/状态/变更事件，继承 {@link DomainEvent}，
 * 事件类型常量统一取自 {@link ModuleEventTypes}（PROJECT_CREATED / PROJECT_STATUS_CHANGED /
 * PROJECT_INITIATION_APPROVED / PROJECT_CHANGE_APPROVED）。
 *
 * <p><b>发布方式：</b>
 * <pre>{@code
 * applicationEventPublisher.publishEvent(
 *     ProjectDomainEvent.of(ModuleEventTypes.PROJECT_CREATED, projectId, Map.of("code", "P-2026-001")));
 * }</pre>
 *
 * <p><b>消费方式（推荐事务提交后）：</b>
 * <pre>{@code
 * &#64;TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * public void onProjectCreated(ProjectDomainEvent event) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public class ProjectDomainEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 构造项目域事件。
     *
     * @param eventType    事件类型（取自 {@link ModuleEventTypes}）
     * @param projectId    项目 ID（映射为 aggregateId）
     * @param aggregateType 聚合根类型（PROJECT / PROJECT_CHANGE）
     * @param metadata     扩展元数据
     */
    public ProjectDomainEvent(String eventType, String projectId, String aggregateType,
                              Map<String, Object> metadata) {
        super(UUID.randomUUID().toString(), LocalDateTime.now(), eventType,
                projectId, aggregateType,
                metadata != null ? metadata : Collections.emptyMap());
    }

    /**
     * 便捷工厂：创建项目域事件。
     *
     * @param eventType  事件类型（取自 {@link ModuleEventTypes}）
     * @param projectId  项目 ID
     * @param metadata   扩展元数据
     * @return 项目域事件实例
     */
    public static ProjectDomainEvent of(String eventType, String projectId, Map<String, Object> metadata) {
        return new ProjectDomainEvent(eventType, projectId, "PROJECT", metadata);
    }

    /**
     * 获取项目 ID（即 aggregateId，语义别名）。
     *
     * @return 项目 ID
     */
    public String getProjectId() {
        return getAggregateId();
    }
}
