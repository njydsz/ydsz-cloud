package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 运行时任务命令 DTO（CUD 操作入参）。
 *
 * <p>用于 FlowRunTaskRepository 的 save/update 方法入参，
 * 符合 §34.2.1（dto/ 命令请求参数 以 DTO 结尾）。
 *
 * <p><b>命名合规说明（1.0.0 DDD 分层规范）：</b>CUD 入参必须是 dto/ 下的 DTO 对象，
 * 禁止使用 VO（符合 §34.2.1）。
 *
 * <p><b>字段对齐说明：</b>本 DTO 字段覆盖 {@link com.njydsz.workflow.domain.vo.FlowRunTaskVO} 中
 * 所有"写入路径"需要的业务属性。Repository.save(dto) 返回带 id / 审计字段的 VO，
 * Service 层不应先 new VO 充当 DTO 再用 BeanUtils 拷贝 —— 应直接 new DTO 填充数据。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowRunTaskDTO {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID（更新时必填） */
  private String id;

  /** 流程实例 ID */
  private String instanceId;

  /** 流程定义 ID */
  private String definitionId;

  /** 流程编码 */
  private String flowCode;

  /** 流程名称 */
  private String flowName;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 节点类型（com.njydsz.workflow.domain.enums.FlowNodeType.code） */
  private Integer nodeType;

  /** 任务标题 */
  private String title;

  /** 委托人 ID（委派操作产生） */
  private String assignorId;

  /** 委托人名称 */
  private String assignorName;

  /** 办理人类型（USER / ROLE / DEPT / POST — FlowAssigneeType.name） */
  private String assigneeType;

  /** 办理人 ID */
  private String assigneeId;

  /** 办理人名称 */
  private String assigneeName;

  /** 办理人权限标识（原始 SpEL 表达式，存档便于回溯） */
  private String permissionFlag;

  /** 办理方式（OR=或签 / PARALLEL=会签 / WEIGHTED=票签 — FlowPerformType.name） */
  private String performType;

  /** 会签所需通过人数（PARALLEL 模式） */
  private Integer approveCount;

  /** 会签当前已通过人数 */
  private Integer approveFinished;

  /** 投票通过率阈值（0~1，默认 0.5） */
  private BigDecimal votePassRate;

  /** 任务状态（PENDING / CLAIMED / PASSED / REJECTED 等 — FlowTaskStatus.name） */
  private String taskStatus;

  /** 任务意见/备注 */
  private String comment;

  /** 签收时间 */
  private LocalDateTime claimAt;

  /** 完成时间 */
  private LocalDateTime finishAt;

  /** 生效时间（P2-1 补录审批） */
  private LocalDateTime effectiveTime;

  /** 完成时间（驳回场景使用） */
  private LocalDateTime completedAt;

  /** 耗时（毫秒） */
  private Long durationMs;

  /** 截止时间（SLA 计算） */
  private LocalDateTime dueAt;

  /** 优先级 */
  private Integer priority;

  /** 已发送 SLA 催办次数 */
  private Integer urgeCount;

  /** 最近一次催办时间 */
  private LocalDateTime lastUrgedAt;

  /** 最终触发的 SLA 动作（REMIN / ESCALATE / AUTO_PASS / AUTO_REJECT） */
  private String slaAction;

  /** SLA 是否已升级（0=否 / 1=是） */
  private Integer slaEscalated;

  /** 乐观锁版本号（更新场景必填，MpBaseEntity 继承） */
  private Integer revision;

  /** 当前办理人权重（票签模式有效） */
  private Integer userWeight;

  /** 累计已通过权重（票签模式） */
  private Integer approveWeight;

  /** 节点总权重（票签模式） */
  private Integer totalWeight;

  /** FOREACH 迭代元素值（非循环节点为 null） */
  private String iterVar;

  /** 业务类型 */
  private String businessType;

  /** 业务单据 ID */
  private String businessId;

  /** 业务单据编号 */
  private String businessNo;

  /** 租户 ID */
  private String tenantId;

  /** 链路追踪 ID */
  private String providerTraceId;

  /** 删除标记（0=未删除，1=已删除） */
  private Integer deleted;

  /** 创建时间 */
  private LocalDateTime createdAt;
}
