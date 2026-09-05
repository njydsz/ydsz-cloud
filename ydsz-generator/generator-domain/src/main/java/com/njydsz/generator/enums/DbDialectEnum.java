package com.njydsz.generator.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据库方言枚举。
 *
 * <p>定义支持的数据库类型及其对应的 JDBC URL 前缀。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Getter
@AllArgsConstructor
public enum DbDialectEnum {

  /** MySQL 数据库。 */
  MYSQL("mysql", "jdbc:mysql://", "com.mysql.cj.jdbc.Driver"),
  /** PostgreSQL 数据库。 */
  POSTGRESQL("postgresql", "jdbc:postgresql://", "org.postgresql.Driver"),
  /** Oracle 数据库（需自行引入驱动）。 */
  ORACLE("oracle", "jdbc:oracle:thin:@", "oracle.jdbc.OracleDriver");

  /** 数据源展示名。 */
  private final String dialect;
  /** JDBC URL 前缀。 */
  private final String urlPrefix;
  /** JDBC 驱动类名。 */
  private final String driverClass;

  /**
   * 根据 URL 解析方言。
   *
   * @param jdbcUrl JDBC URL（如 jdbc:mysql://localhost:3306/db）
   * @return 匹配的方言枚举，未匹配返回 MYSQL 作为默认
   */
  public static DbDialectEnum fromUrl(String jdbcUrl) {
    if (jdbcUrl == null || jdbcUrl.isBlank()) {
      return MYSQL;
    }
    for (DbDialectEnum dialect : values()) {
      if (jdbcUrl.startsWith(dialect.getUrlPrefix())) {
        return dialect;
      }
    }
    return MYSQL;
  }
}
