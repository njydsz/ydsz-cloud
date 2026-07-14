package com.njydsz.pmis.common.domain.entity;

import java.time.LocalDateTime;

/**
 * 可审计实体接。
 *
 * <p>提供创建。时间、更新人/时间的标准访问方法。
 * 与 {@link BaseAuditEntity} 提供 JDBC 自动填充实现。
 * 也可由业务代码手动设置。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public interface Auditable {

    /**
     * 获取创建。
     *
     * @return 创建人标。
     */
    String getCreatedBy();

    /**
     * 设置创建。
     *
     * @param createdBy 创建人标。
     */
    void setCreatedBy(String createdBy);

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    LocalDateTime getCreatedAt();

    /**
     * 设置创建时间
     *
     * @param createdAt 创建时间
     */
    void setCreatedAt(LocalDateTime createdAt);

    /**
     * 获取更新。
     *
     * @return 更新人标。
     */
    String getUpdatedBy();

    /**
     * 设置更新。
     *
     * @param updatedBy 更新人标。
     */
    void setUpdatedBy(String updatedBy);

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    LocalDateTime getUpdatedAt();

    /**
     * 设置更新时间
     *
     * @param updatedAt 更新时间
     */
    void setUpdatedAt(LocalDateTime updatedAt);
}
