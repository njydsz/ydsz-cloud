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
  @NotBlank(message = "资源类型不能为空")
  private String resourceType;

  /** 资源唯一标识（如配置键、字典类型编码、变量键） */
  @NotBlank(message = "资源标识不能为空")
  @Size(max = 128, message = "资源标识长度不能超过 128")
  private String resourceKey;

  /** 资源分组（仅 CONFIG 类型有值） */
  @Size(max = 64, message = "资源分组长度不能超过 64")
  private String resourceGroup;

  /** 变更操作类型（CREATE / UPDATE / DELETE） */
  @NotBlank(message = "变更类型不能为空")
  private String changeType;

  /** 变更前的 JSON 值（CREATE 时为空） */
  private String beforeJson;

  /** 变更后的 JSON 值（DELETE 时为空） */
  private String afterJson;

  /** 变更原因 */
  @Size(max = 500, message = "变更原因长度不能超过 500")
  private String reason;
}
