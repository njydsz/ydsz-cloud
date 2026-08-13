package com.njydsz.common.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.domain.event.EventRegistry;
import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonFormat;
import com.njydsz.common.json.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 领域基础实体（纯领域，不携带持久化语义）。
 *
 * <p>系统统一的业务实体基类，包含主键、审计字段、领域事件支持。
 * 此类为纯 POJO，不含 MyBatis-Plus 注解，也不含持久化专用字段。
 *
 * <p>实现 {@link EventRegistry} 接口，提供标准化的领域事件注册能力。
 * 任意类均可通过实现此接口获得事件注册能力，不必强制继承此类。
 *
 * <p><b>equals/hashCode 语义：</b>仅以 {@code id} 参与判同（DDD 实体标准语义），
 * 确保持久化实体在集合操作（Set/Map）中的正确行为。
 *
 * <p><b>v1.8.0 变更：</b>实现 {@link EventRegistry} 接口，领域事件能力标准化；
 * equals 语义修正为仅以 id 判同。
 *
 * <p><b>v1.7.0 变更：</b>移除持久化相关字段（revision/deleted/tenantId/status），
 * 这些字段应由 {@code common-jdbc} 的 {@code MpBaseEntity} 承载，保持领域纯净。
 *
 * <p><b>继承关系（根据持久化框架选择）：</b>
 * <ul>
 *   <li>使用 MyBatis-Plus：业务实体继承 {@code com.njydsz.common.jdbc.entity.MpBaseEntity}</li>
 *   <li>使用 JPA：业务实体继承此类 + JPA 注解</li>
 *   <li>纯内存/事件溯源：继承此类 + 自定义事件逻辑</li>
 * </ul>
 *
 * @param <T> 主键ID类型
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.7.0 纯领域化：移除 revision/deleted/tenantId/status 持久化字段
 * @since 1.8.0 实现 EventRegistry 接口
 */
@JsonClass(description = "领域实体基类，纯领域无持久化语义")
@Getter
@Setter
@EqualsAndHashCode(of = {"id"}, callSuper = false)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseEntity<T extends Serializable> implements Serializable, EventRegistry {

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
