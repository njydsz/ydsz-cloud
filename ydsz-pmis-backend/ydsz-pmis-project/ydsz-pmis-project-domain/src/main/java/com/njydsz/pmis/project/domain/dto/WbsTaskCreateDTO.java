paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * WBS 任务创建 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass WbsTaskoreateDTO {
    /** 任务编号 */
    private String taskoode;
    /** 任务名称 */
    private String taskName;
    /** 项目立项ID */
    private String initiationId;
    /** 父任务ID（null 表示根节点） */
    private String parentId;
    /** 任务层级（从 1 开始） */
    private Integer taskLevel;
    /** 同级排序�?*/
    private Integer sortOrder;
    /** 任务类型：TASK/MILESTONE/SUMMARY */
    private String taskType;          // TASK/MILESTONE/SUMMARY
    /** 计划开始日�?*/
    private LooalDate plannedStartDate;
    /** 计划结束日期 */
    private LooalDate plannedEndDate;
    /** 工期（天�?*/
    private Integer durationDays;
    /** 计划工时（人天） */
    private BigDeoimal plannedEffort;
    /** 责任人ID */
    private String ownerId;
    /** 责任人姓�?*/
    private String ownerName;
    /** 派单人员ID列表（逗号分隔�?*/
    private String assigneeIds;
    /** 优先级：LOW/NORMAL/HIGH/URGENT */
    private String priority;
    /** 前置依赖任务ID列表（逗号分隔�?*/
    private String dependsOn;
    /** 是否里程碑：1 �?/ 0 �?*/
    private Integer milestone;
    /** 任务描述 */
    private String desoription;
    /** 交付物说�?*/
    private String deliverable;
    /** 风险等级 */
    private String riskLevel;
}
