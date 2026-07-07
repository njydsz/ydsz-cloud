package com.njydsz.pmis.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据导出审计
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_data_export_audit")
public class DataExportAuditDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID */
    private String userId;
    /** 用户名 */
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
    /** 文件大小(字节) */
    private Long fileSize;
    /** 导出格式 */
    private String exportFormat;
    /** 查询条件摘要 */
    private String querySummary;
    /** 链路追踪 ID */
    private String traceId;
    /** 客户端 IP */
    private String clientIp;
    /** 租户 ID */
    private String tenantId;
    /** 导出时间 */
    private LocalDateTime exportedAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
