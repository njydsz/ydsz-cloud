package com.njydsz.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 配置查询请求 DTO。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@Schema(description = "配置查询请求")
public class ConfigGetRequest {

  /** 配置键 */
  @NotBlank(message = "配置键不能为空")
  @Schema(description = "配置键", required = true)
  private String key;
}
