package com.njydsz.agent.domain.config.properties;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtelProperties {
  private boolean isEnabled = false;
  private String serviceName = "ydsz-agent";
}
