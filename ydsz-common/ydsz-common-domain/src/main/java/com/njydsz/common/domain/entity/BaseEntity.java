package com.njydsz.common.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.event.EventRegistry;
import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonFormat;
import com.njydsz.common.json.annotation.JsonIgnore;

/**
 * 领域基础实体（纯领域，不携带持久化语义）。
 *
 * <p>统一的业务实体基类，包含主键、审计字段，并实现 {@link EventRegistry} 提供领域事件注册能力。
 * equals/hashCode 仅以 {@code id} 参与判同（DDD 实体标准语义）。
 *
 * <p><b>已废弃：</b>当前项目统一使用 {@code ydsz-common-jdbc} 提供的
 * {@link com.njydsz.common.jdbc.entity.MpBaseEntity MpBaseEntity} 作为持久化实体基类（支持 MyBatis-Plus
 * 注解、乐观锁、逻辑删除、多租户等能力）。若需领域事件能力，可直接实现 {@link EventRegistry} 接口。
 *
 * @param <T> 主键 ID 类型
 * @author ydsz-team
 * @since 1.10.0
 * @deprecated 自 1.10.0 起废弃，替代方案为 {@code com.njydsz.common.jdbc.entity.MpBaseEntity}。
 *             需要领域事件能力时请直接实现 {@link EventRegistry} 接口。
 */
@Deprecated(since = "1.10.0", forRemoval = false)
@JsonClass(description = "领域实体基类，纯领域无持久化语义（已废弃，请使用 MpBaseEntity）")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseEntity<T extends Serializable> implements Serializable, EventRegistry {

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
     * 使用 {@code transient} 避免参与 Java 序列化，
     * 配合 {@code @JsonIgnore} 排除在 JSON 序列化之外。
     * 类级 {@code @EqualsAndHashCode(of = "id")} 已排除非 id 字段，无需重复标注 Exclude。
     */
    @JsonIgnore
    @Builder.Default
    private transient List<Object> domainEvents = new ArrayList<>();

    // ======================== 实现 EventRegistry 接口 ========================

    /**
     * 基于 id 判同（DDD 实体标准语义）。
     *
     * <p>与 Lombok 默认 {@code @EqualsAndHashCode(of = "id")} 的区别：
     * 当双方 id 都为 {@code null}（瞬态未持久化）时返回 {@code false}，
     * 避免两个不同瞬态对象因 id 同为 null 被误判为 equal，
     * 防止 Set/Map 中出现意料之外的折叠行为。
     *
     * <p>仅 {@code this == other} 在 id 同为 null 时仍返回 {@code true}（满足自反性）。
     *
     * @param o 比较对象
     * @return id 非 null 且相等时返回 true；双方 id 为 null 时仅同一引用返回 true
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseEntity<?> other = (BaseEntity<?>) o;
        return id != null && java.util.Objects.equals(id, other.id);
    }

    /**
     * 基于 id 计算 hashCode。
     *
     * <p>id 为 null 时使用类身份的常量，保证瞬态对象也能安全用于哈希容器。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return id != null ? java.util.Objects.hash(id) : java.util.Objects.hash(getClass());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerEvent(Object event) {
        if (event == null) {
            throw new IllegalArgumentException("Domain event must not be null");
        }
        if (domainEvents == null) {
            domainEvents = new ArrayList<>();
        }
        domainEvents.add(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object> pullDomainEvents() {
        if (domainEvents == null || domainEvents.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object> getDomainEvents() {
        if (domainEvents == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(domainEvents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearDomainEvents() {
        if (domainEvents != null) {
            domainEvents.clear();
        }
    }

    // ======================== 领域行为 ========================

    /**
     * 判断是否有暂存的领域事件。
     *
     * @return 有领域事件返回 true
     */
    @JsonIgnore
    public boolean hasDomainEvents() {
        return domainEvents != null && !domainEvents.isEmpty();
    }
}
