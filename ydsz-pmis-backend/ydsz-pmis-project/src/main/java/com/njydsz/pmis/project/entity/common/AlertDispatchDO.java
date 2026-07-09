package com.njydsz.pmis.project.entity.common;

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
 * 预警分级推送（P4-2）
 *
 * <p>用于预算/EVM/Bench/质量/可计费利用率等模块的预警消息
 * 按黄/红等级分发到不同层级角色（PM/PMO/GM/CFO）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_alert_dispatch")
public class AlertDispatchDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 预警编码：唯一 */
    private String alertCode;
    /** 预警类型: BUDGET/RISK/EVM/SLA/BENCH/UTILIZATION/QUALITY/OTHER */
    private String alertType;
    /** 预警等级: YELLOW/RED/NORMAL */
    private String alertLevel;
    /** 来源模块: project/execution/finance/agent */
    private String sourceType;
    /** 来源业务主键（可拼接） */
    private String sourceId;
    /** 标题 */
    private String title;
    /** 内容 */
    private String content;
    /** 目标角色 PM/PMO/GM/CFO/HR/ALL */
    private String targetRole;
    /** 指定接收人 ID 列表（逗号分隔） */
    private String targetUserIds;
    /** 推送渠道 INAPP/EMAIL/SMS，逗号分隔 */
    private String pushChannels;
    /** 分发时间 */
    private LocalDateTime dispatchedAt;
    /** 分发人/系统/调度任务名 */
    private String dispatchedBy;
    /** 状态: PENDING/SENT/FAILED/CANCELLED */
    private String status;
    /** 实际发送时间 */
    private LocalDateTime sentAt;
    /** 失败原因 */
    private String failReason;
    /** 重试次数 */
    private Integer retryCount;
    /** 租户ID */
    private String tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
