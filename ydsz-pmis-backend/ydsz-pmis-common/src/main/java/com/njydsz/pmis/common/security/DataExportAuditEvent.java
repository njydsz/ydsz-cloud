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

    private Long userId;
    private String username;
    private String exportModule;
    private String exportAction;
    private String bizType;
    private Integer rowCount;
    private String fileName;
    private Long fileSize;
    private String exportFormat;
    private String querySummary;
    private String traceId;
    private String clientIp;
    private Long tenantId;
    private Long exportedAt;
}
