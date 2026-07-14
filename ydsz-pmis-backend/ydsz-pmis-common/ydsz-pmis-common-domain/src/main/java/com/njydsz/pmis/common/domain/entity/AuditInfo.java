package com.njydsz.pmis.common.domain.entity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 审计信息值对象
 *
 * <p>将创建人/创建时间/更新人/更新时间封装为一个独立的值对象，
 * 支持以组合方式替代继承，降低实体继承链深度
 *
 * <p><b>设计目标：</b>
 * <ul>
 *   <li>不可变性：创建后不可修改，修改操作返回新实例</li>
 *   <li>可组合：实体可通过 {@code @Embedded} 或直接字段方式组合使用</li>
 *   <li>可独立测试：审计信息可脱离实体独立测试和验证</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 从 Auditable 实体提取
 * AuditInfo info = AuditInfo.from(entity);
 *
 * // 创建新实例
 * AuditInfo info = AuditInfo.of("user123", LocalDateTime.now());
 *
 * // 不可变更新
 * AuditInfo updated = info.withUpdate("user456", LocalDateTime.now());
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see Auditable
 * @see BaseValueObject
 */
public final class AuditInfo extends BaseValueObject {

    private static final long serialVersionUID = 1L;

    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    /**
     * 构造审计信息
     *
     * @param createdBy 创建人ID
     * @param createdAt 创建时间
     * @param updatedBy 更新人ID
     * @param updatedAt 更新时间
     */
    public AuditInfo(String createdBy, LocalDateTime createdAt, String updatedBy, LocalDateTime updatedAt) {
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    /**
     * 创建仅包含创建信息的审计信息
     *
     * @param createdBy 创建人ID
     * @param createdAt 创建时间
     * @return 审计信息实例
     */
    public static AuditInfo of(String createdBy, LocalDateTime createdAt) {
        return new AuditInfo(createdBy, createdAt, createdBy, createdAt);
    }

    /**
     * 创建空的审计信息
     *
     * @return 空的审计信息实例
     */
    public static AuditInfo empty() {
        return new AuditInfo(null, null, null, null);
    }

    /**
     * 从 Auditable 实体提取审计信息
     *
     * @param auditable 可审计实体
     * @return 审计信息实例
     */
    public static AuditInfo from(Auditable auditable) {
        if (auditable == null) {
            return empty();
        }
        return new AuditInfo(
                auditable.getCreatedBy(),
                auditable.getCreatedAt(),
                auditable.getUpdatedBy(),
                auditable.getUpdatedAt());
    }

    /**
     * 不可变更新：设置更新人/更新时间
     *
     * @param updatedBy 更新人ID
     * @param updatedAt 更新时间
     * @return 新的审计信息实例
     */
    public AuditInfo withUpdate(String updatedBy, LocalDateTime updatedAt) {
        return new AuditInfo(this.createdBy, this.createdAt, updatedBy, updatedAt);
    }

    /**
     * 判断是否为新建（尚无创建时间）
     *
     * @return 新建返回 true
     */
    public boolean isFresh() {
        return this.createdAt == null;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    protected List<Object> identityValues() {
        return Arrays.asList(createdBy, createdAt, updatedBy, updatedAt);
    }
}
