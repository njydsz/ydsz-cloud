package com.njydsz.userinfo.domain.dto;

import lombok.Data;

import com.njydsz.common.excel.annotation.ExcelProperty;

/**
 * 用户导入 DTO
 *
 * <p>用于 Excel 批量导入用户，字段通过 {@link ExcelProperty} 注解与 Excel 列映射。
 *
 * <p><b>导入字段说明：</b>
 *
 * <ul>
 *   <li>{@code username}：登录用户名（必填，全局唯一）
 *   <li>{@code realName}：真实姓名（必填）
 *   <li>{@code password}：初始密码（必填，需符合密码策略）
 *   <li>{@code phone}：手机号（可选）
 *   <li>{@code email}：邮箱（可选）
 *   <li>{@code deptCode}：部门编码（可选，按编码关联部门）
 *   <li>{@code positionCode}：岗位编码（可选，如 PM/DEV/QA）
 *   <li>{@code leaderUsername}：上级用户名（可选，按用户名关联上级）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserImportDTO {

  /** 登录用户名（必填） */
  @ExcelProperty(value = "用户名", order = 1, required = true)
  private String username;

  /** 真实姓名（必填） */
  @ExcelProperty(value = "真实姓名", order = 2, required = true)
  private String realName;

  /** 初始密码（必填） */
  @ExcelProperty(value = "初始密码", order = 3, required = true)
  private String password;

  /** 手机号（可选） */
  @ExcelProperty(value = "手机号", order = 4)
  private String phone;

  /** 邮箱（可选） */
  @ExcelProperty(value = "邮箱", order = 5)
  private String email;

  /** 部门编码（可选，按部门编码关联） */
  @ExcelProperty(value = "部门编码", order = 6)
  private String deptCode;

  /** 岗位编码（可选，如 PM/DEV/QA/SA） */
  @ExcelProperty(value = "岗位编码", order = 7)
  private String positionCode;

  /** 上级用户名（可选，按用户名关联直属上级） */
  @ExcelProperty(value = "上级用户名", order = 8)
  private String leaderUsername;
}
