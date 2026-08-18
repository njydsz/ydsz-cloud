package com.njydsz.system.domain.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 租户创建/更新 DTO
 *
 * <p>对应 {@code ydsz_tenant} 表的写入参数。 创建时 {@code id} 为空（由雪花算法自动生成），更新时 {@code id} 必填。
 *
 * <p><b>字段约束：</b>
 *
 * <ul>
 *   <li>{@code tenantCode} — 租户编码，全局唯一，最长 64 字符
 *   <li>{@code tenantName} — 租户名称，最长 128 字符
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "租户创建/更新 DTO")
public class TenantDTO {

  @Schema(description = "主键 ID（更新时必填）")
  private String id;

  @NotBlank(message = "租户编码不能为空")
  @Size(max = 64, message = "租户编码长度不能超过64")
  @Xss(message = "租户编码包含非法内容")
  @Schema(description = "租户编码")
  private String tenantCode;

  @NotBlank(message = "租户名称不能为空")
  @Size(max = 128, message = "租户名称长度不能超过128")
  @Xss(message = "租户名称包含非法内容")
  @Schema(description = "租户名称")
  private String tenantName;

  @Size(max = 64, message = "联系人长度不能超过64")
  @Xss(message = "联系人包含非法内容")
  @Schema(description = "联系人姓名")
  private String contactName;

  @Size(max = 32, message = "联系电话长度不能超过32")
  @Schema(description = "联系电话")
  private String contactPhone;

  @Email(message = "邮箱格式不正确")
  @Size(max = 128, message = "邮箱长度不能超过128")
  @Schema(description = "联系邮箱")
  private String contactEmail;

  @Schema(description = "关联套餐 ID")
  private String planId;

  @Schema(description = "订阅到期时间")
  private LocalDateTime expireAt;

  @Schema(description = "独立数据源标识")
  private String datasourceKey;

  @Schema(description = "状态: ENABLED/DISABLED/EXPIRED")
  private String status;

  @Size(max = 512, message = "备注长度不能超过512")
  @Xss(message = "备注包含非法内容")
  @Schema(description = "备注")
  private String remark;
}
