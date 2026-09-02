package com.njydsz.system.domain.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 租户创建/更新 DTO
 *
 * <p>对应 {@code ydsz_sys_tenant} 表的写入参数。 创建时 {@code id} 为空（由雪花算法自动生成），更新时 {@code id} 必填。
 *
 * <p><b>字段约束：</b>
 *
 * <ul>
 *   <li>{@code tenantCode} — 租户编码，全局唯一，最长 64 字符
 *   <li>{@code tenantName} — 租户名称，最长 128 字符
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class TenantDTO {

  private String id;

  @NotBlank(message = "租户编码不能为空")
  @Size(max = 64, message = "租户编码长度不能超过64")
  @Xss(message = "租户编码包含非法内容")
  private String tenantCode;

  @NotBlank(message = "租户名称不能为空")
  @Size(max = 128, message = "租户名称长度不能超过128")
  @Xss(message = "租户名称包含非法内容")
  private String tenantName;

  @Size(max = 64, message = "联系人长度不能超过64")
  @Xss(message = "联系人包含非法内容")
  private String contactName;

  @Size(max = 32, message = "联系电话长度不能超过32")
  private String contactPhone;

  @Email(message = "邮箱格式不正确")
  @Size(max = 128, message = "邮箱长度不能超过128")
  private String contactEmail;

  private String planId;

  private LocalDateTime expireAt;

  private String datasourceKey;

  private String status;

  @Size(max = 512, message = "备注长度不能超过512")
  @Xss(message = "备注包含非法内容")
  private String remark;
}
