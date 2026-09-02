package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 字典类型 DTO
 *
 * <p>用于字典类型的创建和更新操作，作为 Repository 接口 CUD 方法的入参。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code id} — 主键 ID（更新时必填）
 *   <li>{@code typeCode} — 字典类型编码，租户内唯一
 *   <li>{@code typeName} — 字典类型名称
 *   <li>{@code description} — 字典类型业务说明
 *   <li>{@code status} — 启用状态: ENABLED/DISABLED
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class DictTypeDTO {

  private String id;

  @NotBlank(message = "字典类型编码不能为空")
  @Size(max = 64, message = "字典类型编码长度不能超过64")
  @Xss(message = "字典类型编码包含非法内容")
  private String typeCode;

  @NotBlank(message = "字典类型名称不能为空")
  @Size(max = 128, message = "字典类型名称长度不能超过128")
  @Xss(message = "字典类型名称包含非法内容")
  private String typeName;

  @Xss(message = "字典类型业务说明包含非法内容")
  private String description;

  private String status;
}
