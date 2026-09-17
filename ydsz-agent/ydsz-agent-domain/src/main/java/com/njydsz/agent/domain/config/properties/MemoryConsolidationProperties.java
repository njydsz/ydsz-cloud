package com.njydsz.agent.domain.config.properties;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryConsolidationProperties {
  private static final int DEFAULT_BATCH_SIZE = 50;

  private boolean isEnabled = false;
  private boolean isDreamingEnabled = false;
  private int batchSize = DEFAULT_BATCH_SIZE;
  private String cron = "0 30 2 * * ?";
}
