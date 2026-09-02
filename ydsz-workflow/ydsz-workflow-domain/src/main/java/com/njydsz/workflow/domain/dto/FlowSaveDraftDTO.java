package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存流程草稿 DTO
 *
 * <p>借鉴 Flowlong 的「暂存待审」概念，允许用户保存已填写的表单数据为草稿，
 * 后续可修改后重新提交。草稿不触发流程流转，仅保存变量数据。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>用户填写复杂审批表单时临时保存
 *   <li>用户需要补充材料后再提交
 *   <li>多步骤表单的分步保存
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowSaveDraftDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程编码（必填） */
  @NotBlank(message = "{validation.workflow.start.flowCode.required}")
  private String flowCode;

  /** 流程版本（不填则取最新已发布） */
  private String version;

  /** 业务类型（必填） */
  @NotBlank(message = "{validation.workflow.start.bizType.required}")
  private String businessType;

  /** 业务单据 ID（必填） */
  @NotBlank(message = "{validation.workflow.start.bizId.required}")
  private String businessId;

  /** 业务单据编号 */
  private String businessNo;

  /** 流程标题 */
  private String title;

  /** 发起人 ID */
  private String initiatorId;

  /** 发起人姓名 */
  private String initiatorName;

  /** 草稿表单数据（流程变量） */
  private Map<String, Object> draftData;

  /** 租户 ID（不填则取当前用户租户） */
  private String tenantId;

  /** 链路追踪 ID */
  private String providerTraceId;
}
