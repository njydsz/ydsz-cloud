package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 业务流程实例关联实体
 *
 * <p>记录业务单据与流程实例的映射关系，便于反查与统计。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_workflow_business")
public class WorkflowBusinessDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Flowable 流程实例 ID */
    private String processInstanceId;

    /** 流程定义 KEY */
    private String processDefinitionKey;

    /** 流程定义 ID */
    private String processDefinitionId;

    /** 业务类型 */
    private String businessType;

    /** 业务单据 ID */
    private String businessId;

    /** 业务单据编号 */
    private String businessNo;

    /** 流程标题 */
    private String title;

    /** 发起人 ID */
    private Long initiatorId;

    /** 发起人姓名 */
    private String initiatorName;

    /** 当前节点 */
    private String currentNode;

    /** 状态: RUNNING/SUSPENDED/COMPLETED/TERMINATED */
    private String status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 耗时(毫秒) */
    private Long durationMs;

    /** 租户 ID */
    private Long tenantId;

    /** 备注 */
    private String remark;
}
