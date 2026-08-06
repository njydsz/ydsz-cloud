package com.remisoft.common.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
 * 领域基础实体（扁平化、纯领域，不依赖 MyBatis-Plus）。
 *
 * <p>系统统一的业务实体基类，包含主键、审计字段、乐观锁版本、逻辑删除、状态、租户标识等全部通用列。
 * 业务实体直接继承此类即可获得完整的通用字段，无需多层继承。
 *
 * <p><b>纯领域设计：</b>本类不携带任何 MyBatis-Plus 注解，持久化行为全部由 common-jdbc 的
 * SQL 拦截器在 SQL 层完成，实体本身保持领域纯净：
 * <ul>
 *   <li>审计字段填充（createdBy/createdAt/updatedBy/updatedAt）：由 {@code CombinedFieldFillInterceptor} 处理</li>
 *   <li>乐观锁（revision）：由 {@code OptimisticLockInterceptor} 处理</li>
 *   <li>逻辑删除（deleted）：由 {@code LogicalDeleteInterceptor} 处理</li>
 *   <li>列名映射：依赖 MP 全局 {@code map-underscore-to-camel-case}</li>
 *   <li>主键生成：依赖 MP 全局 {@code id-type=ASSIGN_ID}（雪花算法）</li>
 * </ul>
 *
 * <p><b>字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>列名</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，雪花算法生成</td></tr>
 *   <tr><td>createdBy</td><td>created_by</td><td>创建人ID，INSERT 自动填充</td></tr>
 *   <tr><td>createdAt</td><td>created_at</td><td>创建时间，INSERT 自动填充</td></tr>
 *   <tr><td>updatedBy</td><td>updated_by</td><td>更新人ID，INSERT/UPDATE 自动填充</td></tr>
 *   <tr><td>updatedAt</td><td>updated_at</td><td>更新时间，INSERT/UPDATE 自动填充</td></tr>
 *   <tr><td>revision</td><td>revision</td><td>乐观锁版本号，每次更新 +1</td></tr>
 *   <tr><td>deleted</td><td>deleted</td><td>逻辑删除标识（0=未删除，1=已删除）</td></tr>
 *   <tr><td>status</td><td>status</td><td>业务状态标识，子类可覆盖为枚举</td></tr>
 *   <tr><td>tenantId</td><td>tenant_id</td><td>租户ID，多租户隔离</td></tr>
 * </table>
 *
 * @param <T> 主键ID类型，支持 Long、String、UUID 等
 *
 * @author remi-team
 * @since 1.0.0
 * @since 1.4.0 扁平化：合并 BaseIdEntity/BaseAuditEntity，移除 domainEvents，纯领域无 MP 注解
 * @since 1.5.0 增加 isDeleted / markDeleted / markActive 便捷方法，修复 revision NPE 风险
 * @since 1.6.0 恢复领域事件暂存能力（transient + @JsonIgnore，纯领域不影响持久化）
 */
@JsonClass(description = "领域实体基类，标记可安全反序列化")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseEntity<T extends Serializable> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法，由 MP 全局 id-type=ASSIGN_ID 生成） */
    private T id;

    /** 创建人ID（INSERT 时由 CombinedFieldFillInterceptor 自动填充） */
    private String createdBy;

    /** 创建时间（INSERT 时由 CombinedFieldFillInterceptor 自动填充） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新人ID（INSERT/UPDATE 时由 CombinedFieldFillInterceptor 自动填充） */
    private String updatedBy;

    /** 更新时间（INSERT/UPDATE 时由 CombinedFieldFillInterceptor 自动填充） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 乐观锁版本号。
     *
     * <p>每次更新时自动递增（+1），由 {@code OptimisticLockInterceptor} 处理。
     * 使用 {@link Builder.Default} 保证通过 Builder 构建时默认值 0 不丢失。
     * JSON 反序列化可能带来 null，业务代码建议优先使用 {@link #getRevisionSafe()}。
     */
    @Builder.Default
    private int revision = 0;

    /**
     * 逻辑删除标识。
     *
     * <p>兼容 MyBatis-Plus 全局约定：0=未删除，1=已删除。
     * 删除操作转为 UPDATE，查询自动追加 WHERE deleted=0，
     * 由 {@code LogicalDeleteInterceptor} 处理。
     *
     * <p>业务代码建议优先使用 {@link #isDeleted()} / {@link #markDeleted()} / {@link #markActive()}
     * 便捷方法，避免直接操作魔法数字 0/1。
     */
    @JsonIgnore
    @Builder.Default
    private int deleted = 0;

    /**
     * 业务状态标识。
     *
     * <p>子类可按需覆盖为具体业务状态枚举值，默认值为空。
     */
    private String status;

    /**
     * 租户ID。
     *
     * <p>多租户隔离字段，由租户拦截器自动注入 WHERE 条件和 INSERT 填充。
     * 单租户模式下默认值 "1"。对外 API 不暴露。
     */
    @JsonIgnore
    private String tenantId;

    // ======================== 领域事件（transient，不参与序列化） ========================

    /**
     * 领域事件暂存列表。
     *
     * <p>用于 DDD 聚合根在状态变更时注册事件，由 Repository 在持久化后统一分派。
     * 设计参考：
     * <ul>
     *   <li>Spring Data 的 {@code @DomainEvents} / {@code @AfterDomainEventPublication} 回调</li>
     *   <li>Axon Framework 的 {@code AbstractAggregateRoot.registerEvent()}</li>
     * </ul>
     *
     * <p><b>注意：</b>使用 {@code transient} 避免参与 Java 序列化，
     * 配合 {@code @JsonIgnore} 排除在 JSON 序列化之外，保持领域纯净。
     *
     * @since 1.6.0
     */
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Builder.Default
    private transient List<Object> domainEvents = new ArrayList<>();

    // ======================== 领域行为便捷方法 ========================

    /**
     * 注册领域事件。
     *
     * <p>由聚合根在状态变更时调用。事件暂存在实体中，由 Repository 在
     * 持久化后统一分派（调用 {@link #pullDomainEvents()}）。
     *
     * <p>使用示例：
     * <pre>{@code
     * public class Order extends BaseEntity<Long> {
     *     public void pay() {
     *         // 业务逻辑...
     *         this.status = OrderStatus.PAID.name();
     *         registerEvent(new OrderPaidEvent(this.id, this.updatedAt));
     *     }
     * }
     * }</pre>
     *
     * @param event 领域事件对象，非 null
     * @since 1.6.0
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
     * <p>典型分派模式（在 Repository 实现中）：
     * <pre>{@code
     * public Order save(Order order) {
     *     Order saved = orderMapper.insert(order);
     *     List<Object> events = order.pullDomainEvents();
     *     events.forEach(eventPublisher::publishEvent);
     *     return saved;
     * }
     * }</pre>
     *
     * @return 已暂存的领域事件列表（非 null，可能为空列表）
     * @since 1.6.0
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
     * @since 1.6.0
     */
    @JsonIgnore
    public boolean hasDomainEvents() {
        return domainEvents != null && !domainEvents.isEmpty();
    }

    /**
     * 安全获取乐观锁版本号（处理 JSON 反序列化后可能为 null 的场景）。
     *
     * @return 版本号，null 时返回 0
     */
    @JsonIgnore
    public int getRevisionSafe() {
        return revision;
    }

    /**
     * 判断是否已逻辑删除。
     *
     * @return deleted=1 时返回 true
     */
    @JsonIgnore
    public boolean isDeleted() {
        return deleted == 1;
    }

    /**
     * 标记为已逻辑删除（不实际删除记录）。
     *
     * <p>由 LogicalDeleteInterceptor 在 WHERE 条件中自动追加 deleted=0 过滤。
     */
    public void markDeleted() {
        this.deleted = 1;
    }

    /**
     * 标记为未删除（恢复软删除状态）。
     */
    public void markActive() {
        this.deleted = 0;
    }

    /**
     * 判断 revision 字段是否为"初始版本"（0 或 null）。
     *
     * @return 首次插入未更新的实体返回 true
     */
    @JsonIgnore
    public boolean isInitialRevision() {
        return revision == 0;
    }

    /**
     * 获取 status 字段值（安全 null 判断）。
     *
     * @return status 值，可能为 null 或空字符串
     */
    @JsonIgnore
    public boolean hasStatus() {
        return status != null && !status.isBlank();
    }

    /**
     * 判断是否为多租户模式下的身份（tenantId 非空且非默认 "1"）。
     *
     * @return 多租户身份返回 true
     */
    @JsonIgnore
    public boolean isMultiTenant() {
        return tenantId != null && !Objects.equals(tenantId, "1");
    }
}
