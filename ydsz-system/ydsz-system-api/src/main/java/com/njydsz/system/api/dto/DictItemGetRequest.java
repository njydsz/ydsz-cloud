package com.njydsz.system.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典项查询请求 DTO。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "字典项查询请求")
public class DictItemGetRequest {

  /** 字典类型编码 */
  @NotBlank(message = "字典类型编码不能为空")
  @Schema(description = "字典类型编码", required = true)
  private String typeCode;

  /** 字典项编码 */
  @NotBlank(message = "字典项编码不能为空")
  @Schema(description = "字典项编码", required = true)
  private String itemCode;
}
