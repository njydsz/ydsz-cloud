package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Text2SQL 增强五步编排配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.text2sql}，控制自然语言转 SQL 能力的启用状态、
 * 增强模式（五步 StateGraph 编排）、Schema 召回、可行性评估、语义一致性校验、
 * JDBC 连接信息以及查询结果行数与超时限制。默认启用基础能力，增强模式关闭。
 * 一致性阈值默认 0.7，Schema 召回最多 5 张表。
 *
 * @author ydsz
 * @since 26.09.24
 */
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
