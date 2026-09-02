package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 部门请求 DTO。
 *
 * <p>同时用于创建和更新场景：创建时 {@code id} 可不传，更新时 {@code id} 必填。
 * 部门通过 parentId 自关联形成树形组织架构，"0" 表示根节点。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code deptCode} — 部门编码（全局唯一，建议格式 DEPT_XXX），业务侧引用</li>
 *   <li>{@code parentId} — 父部门 ID（"0" 表示根部门）</li>
 *   <li>{@code tenantId} — 租户 ID（多租户场景下区分归属租户）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class DepartmentDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 部门 ID（更新时必填） */
  @Xss(message = "id包含非法内容")
  private String id;

  /** 部门编码（全局唯一，建议格式 {@code DEPT_XXX}） */
  @NotBlank(message = "部门编码不能为空")
  @Size(max = 64, message = "部门编码长度不能超过 64 个字符")
  @Xss(message = "deptCode包含非法内容")
  private String deptCode;

  /** 部门名称（前端展示） */
  @NotBlank(message = "部门名称不能为空")
  @Size(max = 128, message = "部门名称长度不能超过 128 个字符")
  @Xss(message = "deptName包含非法内容")
  private String deptName;

  /** 父部门 ID（{@code "0"} 表示根部门） */
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
