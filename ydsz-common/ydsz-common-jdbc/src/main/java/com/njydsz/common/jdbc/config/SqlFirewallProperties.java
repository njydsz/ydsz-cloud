package com.njydsz.common.jdbc.config;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;
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
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.jdbc.sql-firewall")
public class SqlFirewallProperties {

  /** 是否启用 SQL 防火墙（默认 false） */
  private boolean enabled = false;

  /** 是否拦截 DROP TABLE/DATABASE/INDEX 操作（默认 true） */
  private boolean blockDropTable = true;

  /** 是否拦截 TRUNCATE TABLE 操作（默认 true） */
  private boolean blockTruncate = true;

  /** 是否拦截无 WHERE 条件的 DELETE 操作（默认 true） */
  private boolean blockDeleteWithoutWhere = true;

  /** 是否拦截无 WHERE 条件的 UPDATE 操作（默认 true） */
  private boolean blockUpdateWithoutWhere = true;

  /** 是否拦截分号分隔的多语句执行（默认 true） */
  private boolean blockMultiStatement = true;

  /** 是否拦截 GRANT/REVOKE 权限操作（默认 true） */
  private boolean blockPermissionOps = true;

  /** DROP/TRUNCATE 操作的表白名单（忽略大小写） */
  private Set<String> allowTables = new HashSet<>();
}
