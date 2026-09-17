package com.njydsz.agent.domain.config.properties;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileProperties {
  private static final int DEFAULT_INTERACTION_THRESHOLD = 5;

  private boolean isEnabled = false;
  private int interactionThreshold = DEFAULT_INTERACTION_THRESHOLD;
}
