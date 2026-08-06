package com.remisoft.common.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.remisoft.common.json.annotation.JsonClass;
import com.remisoft.common.json.annotation.JsonFormat;
import com.remisoft.common.json.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 领域基础实体（纯领域，不携带持久化语义）。
 *
 * <p>系统统一的业务实体基类，包含主键、审计字段、领域事件支持。
 * 此类为纯 POJO，不含 MyBatis-Plus 注解，也不含持久化专用字段。
 *
 * <p><b>v1.7.0 变更：</b>移除持久化相关字段（revision/deleted/tenantId/status），
 * 这些字段应由 {@code common-jdbc} 的 {@code MpBaseEntity} 承载，保持领域纯净。
 *
 * <p><b>继承关系（根据持久化框架选择）：</b>
 * <ul>
 *   <li>使用 MyBatis-Plus：业务实体继承 {@code com.remisoft.common.jdbc.entity.MpBaseEntity}</li>
 *   <li>使用 JPA：业务实体继承此类 + JPA 注解</li>
 *   <li>纯内存/事件溯源：继承此类 + 自定义事件逻辑</li>
 * </ul>
 *
 * @param <T> 主键ID类型
 * @author remi-team
 * @since 1.0.0
 * @since 1.7.0 纯领域化：移除 revision/deleted/tenantId/status 持久化字段
 */
@JsonClass(description = "领域实体基类，纯领域无持久化语义")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseEntity<T extends Serializable> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（由持久化框架生成） */
    private T id;

    /** 创建人ID（INSERT 时由持久化层填充） */
    private String createdBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新人ID */
    private String updatedBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // ======================== 领域事件（transient，不参与序列化） ========================

    /**
     * 领域事件暂存列表。
     *
     * <p>用于 DDD 聚合根在状态变更时注册事件，由 Repository 在持久化后统一分派。
     * 设计参考：
     * <ul>
     *   <li>Spring Data 的 {@code @DomainEvents} / {@code @AfterDomainEventPublication}</li>
     *   <li>Axon Framework 的 {@code AbstractAggregateRoot.registerEvent()}</li>
     * </ul>
     *
     * <p>使用 {@code transient} 避免参与 Java 序列化，
     * 配合 {@code @JsonIgnore} 排除在 JSON 序列化之外。
     */
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Builder.Default
    private transient List<Object> domainEvents = new ArrayList<>();

    // ======================== 领域行为 ========================

    /**
     * 注册领域事件。
     *
     * <p>由聚合根在状态变更时调用。事件暂存在实体中，由 Repository 在
     * 持久化后统一分派（调用 {@link #pullDomainEvents()}）。
     *
     * @param event 领域事件对象，非 null
     */
    protected void registerEvent(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("Domain event must not be null");
        }
        if (domainEvents == null) {
            domainEvents = new ArrayList<>();
        }
        domainEvents.add(event);
    }

    /**
     * 获取并清空已暂存的领域事件。
     *
     * <p>由 Repository 在持久化聚合根之后调用（"读出并清空"语义），
     * 获取后实体内不再保留事件，防止事件被重复分派。
     *
     * @return 已暂存的领域事件列表（非 null，可能为空列表）
     */
    public List<Object> pullDomainEvents() {
        if (domainEvents == null || domainEvents.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }

    /**
     * 判断是否有暂存的领域事件。
     *
     * @return 有领域事件返回 true
     */
    @JsonIgnore
    public boolean hasDomainEvents() {
        return domainEvents != null && !domainEvents.isEmpty();
    }

    /**
     * 清除所有领域事件（不返回），适用于不需要派发的场景。
     */
    public void clearDomainEvents() {
        if (domainEvents != null) {
            domainEvents.clear();
        }
    }
}
