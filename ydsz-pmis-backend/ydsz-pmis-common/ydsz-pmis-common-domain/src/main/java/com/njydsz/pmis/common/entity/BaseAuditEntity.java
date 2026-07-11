package com.njydsz.pmis.common.entity;

import java.io.Serializable;
import java.time.Instant;

/**
 * DDD 审计实体基类 —— 带审计字段的聚合根/实体。
 * <p>
 * 对标 remi-comm BaseAuditEntity，在 RootEntity 基础上增加：
 * <ul>
 *   <li>创建人 (createdBy)</li>
 *   <li>更新人 (updatedBy)</li>
 *   <li>逻辑删除标记 (deleted)</li>
 * </ul>
 * </p>
 *
 * @param <ID> 实体标识类型
 * @author njydsz
 * @since 1.0.0
 */
public abstract class BaseAuditEntity<ID extends Serializable> extends BaseEntity<ID> {

    private static final long serialVersionUID = 1L;

    /** 创建人 */
    protected String createdBy;

    /** 更新人 */
    protected String updatedBy;

    /** 逻辑删除标记（0=未删除, 1=已删除） */
    protected Integer deleted;

    // --- Getters & Setters ---

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
