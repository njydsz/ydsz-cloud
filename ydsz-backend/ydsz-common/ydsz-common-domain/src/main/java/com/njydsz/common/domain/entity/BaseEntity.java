package com.njydsz.common.domain.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.domain.annotation.SoftDelete;
import com.njydsz.common.domain.annotation.Version;
import com.njydsz.common.domain.event.DomainEvent;
import com.njydsz.common.json.annotation.YdszJsonField;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 基础实体。
 *
 * <p>继承自 {@link BaseAuditEntity}，包含完整的审计字段、乐观锁版本和逻辑删除标识（0 表示未删除）。
 * 这是系统中最常用的实体基类，适用于大多数业务实体。
 *
 * <p><b>核心特性：</b>
 * <table>
 *   <tr><th>特性</th><th>字段</th><th>说明</th></tr>
 *   <tr><td>审计字段</td><td>createdBy/createdAt/updatedBy/updatedAt</td><td>追踪数据变更</td></tr>
 *   <tr><td>乐观锁</td><td>revision</td><td>并发控制，防止更新冲突</td></tr>
 *   <tr><td>逻辑删除</td><td>deleted</td><td>软删除，数据可恢复</td></tr>
 *   <tr><td>状态标识</td><td>status</td><td>业务状态启用/禁用</td></tr>
 *   <tr><td>领域事件</td><td>domainEvents</td><td>可选的领域事件列表</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class User extends BaseEntity<Long> {
 *
 *     private String username;
 *
 *     private String email;
 *
 *     private String phone;
 *
 *     private String status;
 *
 *     public void changeEmail(String newEmail) {
 *         String oldEmail = this.email;
 *         this.email = newEmail;
 *         registerEvent(new EmailChangedEvent(this.getId(), oldEmail, newEmail));
 *     }
 * }
 * }</pre>
 *
 * @param <T> 主键ID类型，支持 Long、String、UUID 等
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.3.0 简化：移除 AggregateRoot/RootEntity 接口，内联事件管理
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SoftDelete
public class BaseEntity<T> extends BaseAuditEntity<T>
        implements Versionable, SoftDeletable {

    private static final long serialVersionUID = 1L;

    /**
     * 领域事件列表（瞬态，不参与序列化与持久化）
     *
     * <p>可在实体中通过 {@code registerEvent} 注册领域事件，由业务层统一发布。
     * 默认实现为空列表；无事件时避免空指针。
     */
    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    private transient List<DomainEvent> domainEvents;

    /**
     * 乐观锁版本
     *
     * <p>用于并发控制，原理：
     * <ul>
     *   <li>每次更新时自动递增（+1）。</li>
     *   <li>更新 SQL 包含 WHERE 条件：revision = oldRevision</li>
     *   <li>若影响行数为0，说明版本已变化，抛出乐观锁异常</li>
     * </ul>
     *
     * @see Version
     */
    @Version
    @Builder.Default
    private Integer revision = 0;

    /**
     * 逻辑删除标识
     *
     * <p>用于实现软删除，原理：
     * <ul>
     *   <li>删除操作变为 UPDATE 设置 deleted = 1</li>
     *   <li>查询操作自动添加 WHERE deleted = 0 条件</li>
     *   <li>数据可恢复，适合重要业务数据</li>
     * </ul>
     */
    @YdszJsonField(ignore = true)
    private Integer deleted;

    /**
     * 状态标识
     *
     * <p>用于标识实体的业务状态，子类可按需覆盖为具体业务状态枚举值。
     * 默认值为空，由各子类根据业务语义自行定义。
     */
    private String status;

    // ==================== 领域事件管理 ====================

    /**
     * 注册领域事件
     *
     * @param event 领域事件
     * @since 1.3.0 从 AggregateRoot 接口简化为内联方法
     */
    public void registerEvent(DomainEvent event) {
        getDomainEvents().add(event);
    }

    /**
     * 获取已注册的领域事件
     *
     * @return 领域事件列表
     * @since 1.3.0 从 AggregateRoot 接口简化为内联方法
     */
    public List<DomainEvent> getDomainEvents() {
        if (domainEvents == null) {
            domainEvents = new ArrayList<>();
        }
        return domainEvents;
    }

    /**
     * 清空已注册的领域事件
     *
     * @since 1.3.0 从 AggregateRoot 接口简化为内联方法
     */
    public void clearDomainEvents() {
        if (domainEvents != null) {
            domainEvents.clear();
        }
    }
}
