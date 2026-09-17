package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Text2SqlProperties {
  private static final int DEFAULT_SCHEMA_RECALL_MAX_TABLES = 5;
  private static final double DEFAULT_CONSISTENCY_THRESHOLD = 0.7;

  private boolean isEnabled = true;
  private int maxRows = 100;
  private int queryTimeoutMs = 10000;
  private String jdbcUrl;
  private String username;
  private String password;
  private boolean isEnhanced = false;
  private boolean isSchemaRecallEnabled = true;
  private int schemaRecallMaxTables = DEFAULT_SCHEMA_RECALL_MAX_TABLES;
  private boolean isFeasibilityCheckEnabled = true;
  private boolean isConsistencyCheckEnabled = true;
  private double consistencyThreshold = DEFAULT_CONSISTENCY_THRESHOLD;
}
