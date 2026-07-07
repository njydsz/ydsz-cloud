package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
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
    private static final String serialVersionUID = "1";

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编码（TK-YYYYMMDD-XXXX） */
    private String ticketCode;
    /** 项目立项ID */
    private String initiationId;
    /** 关联质保单ID（可空） */
    private String warrantyId;
    /** 工单标题 */
    private String title;
    /** 工单描述 */
    private String description;
    /** BUG/DATA/CONFIG/PROCESS/OTHER */
    private String category;
    /** OpsTicketPriority.code P1-P4 */
    private String priority;
    /** OpsTicketStatus.code */
    private String status;
    /** 报告人ID */
    private String reporterId;
    /** 报告人姓名 */
    private String reporterName;
    /** 报告人电话 */
    private String reporterPhone;
    /** 处理人ID */
    private String assigneeId;
    /** 处理人姓名 */
    private String assigneeName;
    /** 受理时间 */
    private LocalDateTime acceptedAt;
    /** 开始处理时间 */
    private LocalDateTime startedAt;
    /** 解决时间 */
    private LocalDateTime resolvedAt;
    /** 关闭时间 */
    private LocalDateTime closedAt;
    /** 首次响应截止时间 */
    private LocalDateTime responseDueAt;
    /** 解决截止时间 */
    private LocalDateTime resolveDueAt;
    /** 首次响应是否超时 */
    private Boolean responseBreached;
    /** 解决是否超时 */
    private Boolean resolveBreached;
    /** 解决说明 */
    private String resolutionNote;
    /** 客户评分（1-5） */
    private Integer customerScore;
    /** 客户评价内容 */
    private String customerComment;
    /** 附件文件ID列表（逗号分隔） */
    private String fileIds;
    /** 租户ID */
    private String tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 乐观锁版本号（P1-12） */
    @Version
    private Integer version;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
