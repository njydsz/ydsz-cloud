package com.njydsz.pmis.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 日志类实体基类（P1-6 架构优化）。
 *
 * <p>适用于 append-only 的日志/审计/追踪类实体（操作日志、登录审计、数据导出审计、
 * 敏感操作记录、三方回调日志等），这类实体的共性：
 * <ul>
 *   <li>仅有创建人 / 创建时间，无更新人 / 更新时间（记录一旦写入不再修改）</li>
 *   <li>无逻辑删除（日志按期归档或物理删除，不做软删除）</li>
 *   <li>无乐观锁（不支持并发更新）</li>
 * </ul>
 *
 * <p>与 {@link BaseDO} 的区别：
 * <ul>
 *   <li>{@link BaseDO}：createdBy + createdAt + updatedBy + updatedAt + deleted（完整审计 + 软删除）</li>
 *   <li>{@code LogBaseDO}：createdBy + createdAt（仅创建审计，无更新 / 无软删除）</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 * @TableName("pmis_operation_log")
 * public class OperationLogDO extends LogBaseDO {
 *     // 只需定义日志业务字段
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Data
public abstract class LogBaseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建人 ID（雪花算法字符串），由 AuditFieldFiller 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间，由 AuditFieldFiller 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
