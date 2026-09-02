package com.njydsz.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 系统变量创建/更新 DTO
 *
 * <p>对应 {@code ydsz_sys_variable} 表的写入参数，是「系统变量中心」创建 / 更新接口的入参载体。
 * 创建时 {@code id} 为空（由雪花算法自动生成），更新时 {@code id} 必填。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code variableKey} — 变量键，租户内唯一
 *   <li>{@code variableValue} — 变量值
 *   <li>{@code valueType} — 值类型: STRING/NUMBER/BOOLEAN/JSON
 *   <li>{@code status} — 启用状态: ENABLED/DISABLED
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class VariableDTO {

  private String id;

  @NotBlank(message = "变量键不能为空")
  @Size(max = 128, message = "变量键长度不能超过128")
  @Xss(message = "变量键包含非法内容")
  private String variableKey;

  @Xss(message = "变量值包含非法内容")
  private String variableValue;

  @NotBlank(message = "值类型不能为空")
  private String valueType;

  @Xss(message = "变量说明包含非法内容")
  private String description;

  private String status;
}
