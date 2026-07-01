package com.njydsz.pmis.common.security;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 数据导出审计事件
 *
 * <p>由 {@code @DataExportAudit} 注解 AOP 发布，audit 模块异步落库。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
public class DataExportAuditEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 操作用户 ID */
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 导出模块 */
    private String exportModule;

    /** 导出动作 */
    private String exportAction;

    /** 业务类型 */
    private String bizType;

    /** 导出行数 */
    private Integer rowCount;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 导出格式（如 xlsx/csv） */
    private String exportFormat;

    /** 查询条件摘要 */
    private String querySummary;

    /** 链路追踪 ID */
    private String traceId;

    /** 客户端 IP */
    private String clientIp;

    /** 租户 ID */
    private Long tenantId;

    /** 导出时间戳（毫秒） */
    private Long exportedAt;
}
