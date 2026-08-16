package com.njydsz.userinfo.domain.dto.put;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 部门修改请求 DTO。
 *
 * <p>对应后端 {@code PUT /api/v1/department} 请求体。 修改时 {@link #id} 必填，其余字段按需填写，未传字段保持原值不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class DepartmentPutDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 部门 ID（必填） */
  @NotBlank(message = "ID不能为空")
  @Xss(message = "id包含非法内容")
  private String id;

  /** 部门编码（全局唯一） */
  @NotBlank(message = "部门编码不能为空")
  @Size(max = 64, message = "部门编码长度不能超过 64 个字符")
  @Xss(message = "deptCode包含非法内容")
  private String deptCode;

  /** 部门名称 */
  @NotBlank(message = "部门名称不能为空")
  @Size(max = 128, message = "部门名称长度不能超过 128 个字符")
  @Xss(message = "deptName包含非法内容")
  private String deptName;

  /** 父部门 ID */
  @Xss(message = "parentId包含非法内容")
  private String parentId;

  /** 部门描述 */
  @Size(max = 500, message = "描述长度不能超过 500 个字符")
  @Xss(message = "description包含非法内容")
  private String description;

  /** 同级排序序号（升序） */
  private Integer sortOrder;

  /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
  @Xss(message = "status包含非法内容")
  private String status;

  /** 租户 ID */
  @Xss(message = "tenantId包含非法内容")
  private String tenantId;
}
