package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 API Key 请求体。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Data
public class ApiKeyCreateDTO {

  /** Key 名称（用于标识用途，如 "jenkins-deploy", "datadog-monitor"） */
  @NotBlank(message = "Key 名称不能为空")
  @Size(max = 64, message = "Key 名称长度不能超过 64")
  private String keyName;

  /**
   * 授权范围（逗号分隔）。
   *
   * <p>预定义值：{@code read}、{@code write}、{@code admin}、{@code user:read}、{@code user:write} 等。
   */
  private String scopes;

  /** 过期时间（天数），为空表示永不过期 */
  private Integer expireDays;

  /** 每分钟请求限流阈值，为空表示使用默认配置 */
  private Integer rateLimit;
}
