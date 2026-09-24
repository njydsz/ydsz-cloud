package com.njydsz.agent.domain.config.properties;

import java.time.Duration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 安全护栏配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.guardrail}，控制输入提示注入检测、输出 PII 脱敏、
 * 请求限流与幂等去重窗口。各子防护均可独立开关，限流默认每分钟 60 请求，
 * 幂等去重窗口默认 60 秒，默认整体启用。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuardrailProperties {
  private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 60;
  private static final int DEFAULT_IDEMP_TTL_SECONDS = 60;

  private boolean isEnabled = true;
  private boolean isInputGuardrailEnabled = true;
  private boolean isOutputGuardrailEnabled = true;
  private boolean isPiiMaskingEnabled = true;
  private Duration idempotentTtl = Duration.ofSeconds(DEFAULT_IDEMP_TTL_SECONDS);
  private Duration rateWindow = Duration.ofMinutes(1);
  private int maxRequestsPerMinute = DEFAULT_MAX_REQUESTS_PER_MINUTE;
  private String rejectionMessage = "请求被安全护栏拒绝";
}
