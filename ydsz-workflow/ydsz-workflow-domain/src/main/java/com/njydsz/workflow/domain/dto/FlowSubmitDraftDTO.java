package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交流程草稿 DTO
 *
 * <p>将已有草稿正式提交，触发流程流转。提交后实例状态从 DRAFT → RUNNING，
 * 等同于正常启动流程。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>用户完成草稿填写后正式提交审批
 *   <li>系统自动提交到期草稿（cronjob 触发）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowSubmitDraftDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 草稿实例 ID（必填） */
  @NotBlank(message = "{validation.workflow.instance.id.required}")
  private String instanceId;

  /** 更新后的表单数据（可选，不传则使用草稿数据） */
  private Map<String, Object> draftData;

  /** 操作人 ID */
  private String operatorId;

  /** 链路追踪 ID */
  private String providerTraceId;
}
