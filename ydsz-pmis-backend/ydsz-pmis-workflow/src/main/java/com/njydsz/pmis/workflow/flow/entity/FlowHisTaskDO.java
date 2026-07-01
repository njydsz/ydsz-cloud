package com.njydsz.pmis.workflow.flow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 历史任务 DO
 *
 * <p>对标 Warm-Flow flow_his_task，已完成任务归档，避免主表膨胀。<br>
 * 设计要点：created_at 复用 BaseDO 字段，但关闭自动填充，归档时由业务代码显式从源 task.createdAt 复制（保留业务创建时间，非归档时间）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_his_task")
public class FlowHisTaskDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;
    private Long taskId;
    private String flowCode;
    private Long definitionId;
    private String nodeCode;
    private String nodeName;
    private Integer nodeType;
    private String businessType;
    private String businessId;
    private String businessNo;
    private String flowName;
    private String title;
    private String assigneeType;
    private String assigneeId;
    private String assigneeName;
    private String performType;
    private Integer approveCount;
    private Integer approveFinished;
    private String taskStatus;
    private String comment;

    /**
     * 任务原始创建时间（业务时间）
     * <p>关闭 MyBatis Plus 自动填充：INSERT 时由归档逻辑从源 task.createdAt 显式复制，保留业务时间语义。
     */
    @TableField(value = "created_at", fill = FieldFill.NONE)
    @Override
    public LocalDateTime getCreatedAt() { return super.getCreatedAt(); }

    private LocalDateTime claimAt;
    private LocalDateTime finishAt;
    private Long durationMs;
    private Long tenantId;
    private String providerTraceId;
}
