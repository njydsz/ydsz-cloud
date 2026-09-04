package com.njydsz.generator.config;

import java.util.Map;

/**
 * 数据库方言枚举。
 *
 * <p>定义支持的数据库类型，提供各数据库特有的 SQL 和 JDBC 类型映射。
 *
 * @author ydsz-team
 * @since 26.09.04
 */
public enum DatabaseDialect {

  /** MySQL 数据库 */
  MYSQL("mysql", "com.mysql.cj.jdbc.Driver") {
    @Override
    public Map<String, String[]> getTypeMapping() {
      return Map.ofEntries(
          Map.entry("varchar", new String[]{"String", "java.lang.String"}),
          Map.entry("char", new String[]{"String", "java.lang.String"}),
          Map.entry("text", new String[]{"String", "java.lang.String"}),
          Map.entry("longtext", new String[]{"String", "java.lang.String"}),
          Map.entry("int", new String[]{"Integer", "java.lang.Integer"}),
          Map.entry("tinyint", new String[]{"Integer", "java.lang.Integer"}),
          Map.entry("smallint", new String[]{"Integer", "java.lang.Integer"}),
          Map.entry("mediumint", new String[]{"Integer", "java.lang.Integer"}),
          Map.entry("bigint", new String[]{"Long", "java.lang.Long"}),
          Map.entry("decimal", new String[]{"BigDecimal", "java.math.BigDecimal"}),
          Map.entry("numeric", new String[]{"BigDecimal", "java.math.BigDecimal"}),
          Map.entry("datetime", new String[]{"LocalDateTime", "java.time.LocalDateTime"}),
          Map.entry("timestamp", new String[]{"LocalDateTime", "java.time.LocalDateTime"}),
          Map.entry("date", new String[]{"LocalDate", "java.time.LocalDate"}),
          Map.entry("time", new String[]{"LocalDateTime", "java.time.LocalDateTime"}),
          Map.entry("bit", new String[]{"Boolean", "java.lang.Boolean"}),
          Map.entry("json", new String[]{"String", "java.lang.String"})
      );
    }
  },

  /** PostgreSQL 数据库 */
  POSTGRESQL("postgresql", "org.postgresql.Driver") {
    @Override
    public Map<String, String[]> getTypeMapping() {
      return Map.ofEntries(
          Map.entry("varchar", new String[]{"String", "java.lang.String"}),
          Map.entry("text", new String[]{"String", "java.lang.String"}),
          Map.entry("int2", new String[]{"Integer", "java.lang.Integer"}),
          Map.entry("int4", new String[]{"Integer", "java.lang.Integer"}),
          Map.entry("int8", new String[]{"Long", "java.lang.Long"}),
          Map.entry("numeric", new String[]{"BigDecimal", "java.math.BigDecimal"}),
          Map.entry("bool", new String[]{"Boolean", "java.lang.Boolean"}),
          Map.entry("timestamp", new String[]{"LocalDateTime", "java.time.LocalDateTime"}),
          Map.entry("date", new String[]{"LocalDate", "java.time.LocalDate"}),
          Map.entry("jsonb", new String[]{"String", "java.lang.String"}),
          Map.entry("json", new String[]{"String", "java.lang.String"}),
          Map.entry("uuid", new String[]{"String", "java.lang.String"})
      );
    }
  },

  /** Oracle 数据库 */
  ORACLE("oracle", "oracle.jdbc.OracleDriver") {
    @Override
    public Map<String, String[]> getTypeMapping() {
      return Map.ofEntries(
          Map.entry("varchar2", new String[]{"String", "java.lang.String"}),
          Map.entry("nvarchar2", new String[]{"String", "java.lang.String"}),
          Map.entry("clob", new String[]{"String", "java.lang.String"}),
          Map.entry("nclob", new String[]{"String", "java.lang.String"}),
          Map.entry("number", new String[]{"BigDecimal", "java.math.BigDecimal"}),
          Map.entry("int", new String[]{"Integer", "java.lang.Integer"}),
          Map.entry("integer", new String[]{"Integer", "java.lang.Integer"}),
          Map.entry("long", new String[]{"Long", "java.lang.Long"}),
          Map.entry("date", new String[]{"LocalDateTime", "java.time.LocalDateTime"}),
          Map.entry("timestamp", new String[]{"LocalDateTime", "java.time.LocalDateTime"}),
          Map.entry("blob", new String[]{"byte[]", "[B"})
      );
    }
  };

  /** 数据库类型标识（URL 中的关键字） */
  private final String identifier;

  /** JDBC 驱动类全名 */
  private final String driverClassName;

  DatabaseDialect(String identifier, String driverClassName) {
    this.identifier = identifier;
    this.driverClassName = driverClassName;
  }

  public String getIdentifier() {
    return identifier;
  }

  public String getDriverClassName() {
    return driverClassName;
  }

  /**
   * 获取各数据库的 JDBC 类型名 → Java 类型映射。
   *
   * @return 类型映射表（key = JDBC 类型名，value = [短名, 全限定名]）
   */
  public abstract Map<String, String[]> getTypeMapping();

  /**
   * 根据 JDBC 类型名获取对应的 Java 类型。
   *
   * @param jdbcTypeName - JDBC 类型名（如 varchar, int4）
   * @return [短名, 全限定名]，未找到默认 String
   */
  public String[] resolveJavaType(String jdbcTypeName) {
    if (jdbcTypeName == null) {
      return new String[]{"String", "java.lang.String"};
    }
    Map<String, String[]> mapping = getTypeMapping();
    String[] result = mapping.get(jdbcTypeName.toLowerCase());
    return result != null ? result : new String[]{"String", "java.lang.String"};
  }

  /**
   * 根据 JDBC URL 识别数据库类型。
   *
   * @param url - JDBC URL
   * @return 识别的数据库方言，未识别返回 POSTGRESQL 作为默认
   */
  public static DatabaseDialect fromJdbcUrl(String url) {
    if (url == null || url.isBlank()) {
      return POSTGRESQL;
    }
    String lower = url.toLowerCase();
    for (DatabaseDialect dialect : values()) {
      if (lower.contains(":" + dialect.identifier + ":") || lower.startsWith("jdbc:" + dialect.identifier)) {
        return dialect;
      }
    }
    return POSTGRESQL;
  }
}
