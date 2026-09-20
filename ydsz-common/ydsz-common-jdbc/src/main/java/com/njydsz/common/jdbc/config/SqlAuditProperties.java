package com.njydsz.common.jdbc.config;

import java.util.List;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SQL 审计配置属性
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     sql-audit:
 *       enabled: true
 *       audit-select: false
 *       audit-insert: true
 *       audit-update: true
 *       audit-delete: true
 *       log-parameters: true
 *       max-parameter-length: 500
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.jdbc.sql-audit")
public class SqlAuditProperties {

  private boolean isEnabled = false;
  private boolean isAuditSelect = false;
  private boolean isAuditInsert = true;
  private boolean isAuditUpdate = true;
  private boolean isAuditDelete = true;
  private boolean isLogParameters = true;

  @Min(1)
  private int maxParameterLength = 500;

  private List<String> excludeTables;
  private List<String> excludeMethods;

  public boolean isEnabled() { return isEnabled; }
  public void setIsEnabled(boolean isEnabled) { this.isEnabled = isEnabled; }

  public boolean isAuditSelect() { return isAuditSelect; }
  public void setAuditSelect(boolean isAuditSelect) { this.isAuditSelect = isAuditSelect; }

  public boolean isAuditInsert() { return isAuditInsert; }
  public void setAuditInsert(boolean isAuditInsert) { this.isAuditInsert = isAuditInsert; }

  public boolean isAuditUpdate() { return isAuditUpdate; }
  public void setAuditUpdate(boolean isAuditUpdate) { this.isAuditUpdate = isAuditUpdate; }

  public boolean isAuditDelete() { return isAuditDelete; }
  public void setAuditDelete(boolean isAuditDelete) { this.isAuditDelete = isAuditDelete; }

  public boolean isLogParameters() { return isLogParameters; }
  public void setLogParameters(boolean isLogParameters) { this.isLogParameters = isLogParameters; }

  public int getMaxParameterLength() { return maxParameterLength; }
  public void setMaxParameterLength(int maxParameterLength) { this.maxParameterLength = maxParameterLength; }

  public List<String> getExcludeTables() { return excludeTables; }
  public void setExcludeTables(List<String> excludeTables) { this.excludeTables = excludeTables; }

  public List<String> getExcludeMethods() { return excludeMethods; }
  public void setExcludeMethods(List<String> excludeMethods) { this.excludeMethods = excludeMethods; }
}
