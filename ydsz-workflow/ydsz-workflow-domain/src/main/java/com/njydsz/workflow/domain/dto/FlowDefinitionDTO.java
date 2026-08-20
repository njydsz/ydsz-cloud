package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 流程定义命令 DTO（CUD 操作入参）。
 *
 * <p>用于 FlowDefinitionRepository 的 save/update 方法入参，
 * 符合 §34.2.1（dto/ 命令请求参数 以 DTO 结尾）。
 *
 * <p><b>架构合规说明（v2.23 DDD 分层规范）：</b>CUD 入参必须是 dto/ 下的 DTO 对象，
 * 禁止使用 VO（符合 §34.2.1）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowDefinitionDTO {

  @Serial private static final long serialVersionUID = 1L;

  /** 定义 ID（更新时必填） */
  private String id;

  /** 流程编码 */
  private String flowCode;

  /** 流程名称 */
  private String flowName;

  /** 流程版本号 */
  private Integer flowVersion;

  /** 业务类型 */
  private String businessType;

  /** 租户 ID */
  private String tenantId;

  /** 流程定义 JSON 数据（BPMN/Custom Schema） */
  private String definitionData;

  /** 状态（DRAFT / PUBLISHED / ARCHIVED） */
  private String status;

  /** 创建人 ID */
  private String creatorId;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 发布时间 */
  private LocalDateTime publishedAt;

  /** 删除标记（0=未删除，1=已删除） */
  private Integer deleted;
}
