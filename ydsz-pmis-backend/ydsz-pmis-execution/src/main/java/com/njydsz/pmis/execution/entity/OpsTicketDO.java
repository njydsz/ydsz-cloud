package com.njydsz.pmis.execution.entity;

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
 * 运维工单实体
 *
 * <p>P1-P4 SLA 跟踪：响应超时/解决超时自动标记 breached。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_ops_ticket")
public class OpsTicketDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码（TK-YYYYMMDD-XXXX） */
    private String ticketCode;
    private Long initiationId;
    private Long warrantyId;
    private String title;
    private String description;
    /** BUG/DATA/CONFIG/PROCESS/OTHER */
    private String category;
    /** OpsTicketPriority.code P1-P4 */
    private String priority;
    /** OpsTicketStatus.code */
    private String status;
    private Long reporterId;
    private String reporterName;
    private String reporterPhone;
    private Long assigneeId;
    private String assigneeName;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime responseDueAt;
    private LocalDateTime resolveDueAt;
    private Boolean responseBreached;
    private Boolean resolveBreached;
    private String resolutionNote;
    private Integer customerScore;
    private String customerComment;
    private String fileIds;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
