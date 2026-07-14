ackage com.njydsz.pmis.common.domain.entity;

import java.time.LocalDateTime;

/**
 * 可审计实体接口
 *
 * <p>提供创建人/时间、更新人/时间的标准访问方法。
 * 由 {@link BaseAuditEntity} 提供 JDBC 自动填充实现，
 * 也可由业务代码手动设置。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface Auditable {

    /**
     * 获取创建人
     *
     * @return 创建人标识
     */
    String getCreatedBy();

    /**
     * 设置创建人
     *
     * @param createdBy 创建人标识
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
     * 获取更新人
     *
     * @return 更新人标识
     */
    String getUpdatedBy();

    /**
     * 设置更新人
     *
     * @param updatedBy 更新人标识
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
