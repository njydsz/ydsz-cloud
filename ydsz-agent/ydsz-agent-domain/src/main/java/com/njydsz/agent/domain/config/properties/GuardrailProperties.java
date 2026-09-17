package com.njydsz.agent.domain.config.properties;

import java.time.Duration;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

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
