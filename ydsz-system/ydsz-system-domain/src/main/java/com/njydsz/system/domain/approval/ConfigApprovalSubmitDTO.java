package com.njydsz.system.domain.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 配置变更提交审批请求 DTO。
 *
 * <p>前端在配置管理页面发起审批流时提交，包含资源标识、变更前后值和原因。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Data
public class ConfigApprovalSubmitDTO {

  /** 资源类型（CONFIG / DICT / VARIABLE） */
  @NotBlank(message = "{system.approval.resourceType.required}")
  private String resourceType;

  /** 资源唯一标识（如配置键、字典类型编码、变量键） */
  @NotBlank(message = "{system.approval.resourceKey.required}")
  @Size(max = 128, message = "{system.approval.resourceKey.max}")
  private String resourceKey;

  /** 资源分组（仅 CONFIG 类型有值） */
  @Size(max = 64, message = "{system.approval.resourceGroup.max}")
  private String resourceGroup;

  /** 变更操作类型（CREATE / UPDATE / DELETE） */
  @NotBlank(message = "{system.approval.changeType.required}")
  private String changeType;

  /** 变更前的 JSON 值（CREATE 时为空） */
  private String beforeJson;

  /** 变更后的 JSON 值（DELETE 时为空） */
  private String afterJson;

  /** 变更原因 */
  @Size(max = 500, message = "{system.approval.reason.max}")
  private String reason;
}
