package com.njydsz.system.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 配置查询请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "配置查询请求")
public class ConfigGetRequest {

  /** 配置键 */
  @Schema(description = "配置键", required = true)
  private String key;
}
