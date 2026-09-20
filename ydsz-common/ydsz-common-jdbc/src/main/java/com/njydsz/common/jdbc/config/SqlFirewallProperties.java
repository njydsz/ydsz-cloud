package com.njydsz.common.jdbc.config;

import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQL 防火墙配置属性
 *
 * <p>控制 SQL 防火墙拦截器的行为，包括各类危险 SQL 的拦截开关和白名单配置。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   jdbc:
 *     sql-firewall:
 *       enabled: true
 *       block-drop-table: true
 *       block-truncate: true
 *       block-delete-without-where: true
 *       block-update-without-where: true
 *       block-multi-statement: true
 *       allow-tables: []
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.jdbc.sql-firewall")
public class SqlFirewallProperties {

  private boolean isEnabled = false;
  private boolean isBlockDropTable = true;
  private boolean isBlockTruncate = true;
  private boolean isBlockDeleteWithoutWhere = true;
  private boolean isBlockUpdateWithoutWhere = true;
  private boolean isBlockMultiStatement = true;
  private boolean isBlockPermissionOps = true;
  private Set<String> allowTables = new HashSet<>(16);

  public boolean isEnabled() { return isEnabled; }
  public void setIsEnabled(boolean isEnabled) { this.isEnabled = isEnabled; }

  public boolean isBlockDropTable() { return isBlockDropTable; }
  public void setBlockDropTable(boolean isBlockDropTable) { this.isBlockDropTable = isBlockDropTable; }

  public boolean isBlockTruncate() { return isBlockTruncate; }
  public void setBlockTruncate(boolean isBlockTruncate) { this.isBlockTruncate = isBlockTruncate; }

  public boolean isBlockDeleteWithoutWhere() { return isBlockDeleteWithoutWhere; }
  public void setBlockDeleteWithoutWhere(boolean isBlockDeleteWithoutWhere) { this.isBlockDeleteWithoutWhere = isBlockDeleteWithoutWhere; }

  public boolean isBlockUpdateWithoutWhere() { return isBlockUpdateWithoutWhere; }
  public void setBlockUpdateWithoutWhere(boolean isBlockUpdateWithoutWhere) { this.isBlockUpdateWithoutWhere = isBlockUpdateWithoutWhere; }

  public boolean isBlockMultiStatement() { return isBlockMultiStatement; }
  public void setBlockMultiStatement(boolean isBlockMultiStatement) { this.isBlockMultiStatement = isBlockMultiStatement; }

  public boolean isBlockPermissionOps() { return isBlockPermissionOps; }
  public void setBlockPermissionOps(boolean isBlockPermissionOps) { this.isBlockPermissionOps = isBlockPermissionOps; }

  public Set<String> getAllowTables() { return allowTables; }
  public void setAllowTables(Set<String> allowTables) { this.allowTables = allowTables; }
}
