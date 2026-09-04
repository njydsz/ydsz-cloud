package com.njydsz.generator.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public Map<String, DialectType> getTypeMapping() {
      return Map.ofEntries(
          Map.entry("varchar", new DialectType("String", String.class.getName())),
          Map.entry("char", new DialectType("String", String.class.getName())),
          Map.entry("text", new DialectType("String", String.class.getName())),
          Map.entry("longtext", new DialectType("String", String.class.getName())),
          Map.entry("int", new DialectType("Integer", Integer.class.getName())),
          Map.entry("tinyint", new DialectType("Integer", Integer.class.getName())),
          Map.entry("smallint", new DialectType("Integer", Integer.class.getName())),
          Map.entry("mediumint", new DialectType("Integer", Integer.class.getName())),
          Map.entry("bigint", new DialectType("Long", Long.class.getName())),
          Map.entry("decimal", new DialectType("BigDecimal", BigDecimal.class.getName())),
          Map.entry("numeric", new DialectType("BigDecimal", BigDecimal.class.getName())),
          Map.entry("datetime", new DialectType("LocalDateTime", LocalDateTime.class.getName())),
          Map.entry("timestamp", new DialectType("LocalDateTime", LocalDateTime.class.getName())),
          Map.entry("date", new DialectType("LocalDate", LocalDate.class.getName())),
          Map.entry("time", new DialectType("LocalDateTime", LocalDateTime.class.getName())),
          Map.entry("bit", new DialectType("Boolean", Boolean.class.getName())),
          Map.entry("json", new DialectType("String", String.class.getName()))
      );
    }
  },

  /** PostgreSQL 数据库 */
  POSTGRESQL("postgresql", "org.postgresql.Driver") {
    @Override
    public Map<String, DialectType> getTypeMapping() {
      return Map.ofEntries(
          Map.entry("varchar", new DialectType("String", String.class.getName())),
          Map.entry("text", new DialectType("String", String.class.getName())),
          Map.entry("int2", new DialectType("Integer", Integer.class.getName())),
          Map.entry("int4", new DialectType("Integer", Integer.class.getName())),
          Map.entry("int8", new DialectType("Long", Long.class.getName())),
          Map.entry("numeric", new DialectType("BigDecimal", BigDecimal.class.getName())),
          Map.entry("bool", new DialectType("Boolean", Boolean.class.getName())),
          Map.entry("timestamp", new DialectType("LocalDateTime", LocalDateTime.class.getName())),
          Map.entry("date", new DialectType("LocalDate", LocalDate.class.getName())),
          Map.entry("jsonb", new DialectType("String", String.class.getName())),
          Map.entry("json", new DialectType("String", String.class.getName())),
          Map.entry("uuid", new DialectType("String", String.class.getName()))
      );
    }
  },

  /** Oracle 数据库 */
  ORACLE("oracle", "oracle.jdbc.OracleDriver") {
    @Override
    public Map<String, DialectType> getTypeMapping() {
      return Map.ofEntries(
          Map.entry("varchar2", new DialectType("String", String.class.getName())),
          Map.entry("nvarchar2", new DialectType("String", String.class.getName())),
          Map.entry("clob", new DialectType("String", String.class.getName())),
          Map.entry("nclob", new DialectType("String", String.class.getName())),
          Map.entry("number", new DialectType("BigDecimal", BigDecimal.class.getName())),
          Map.entry("int", new DialectType("Integer", Integer.class.getName())),
          Map.entry("integer", new DialectType("Integer", Integer.class.getName())),
          Map.entry("long", new DialectType("Long", Long.class.getName())),
          Map.entry("date", new DialectType("LocalDateTime", LocalDateTime.class.getName())),
          Map.entry("timestamp", new DialectType("LocalDateTime", LocalDateTime.class.getName())),
          Map.entry("blob", new DialectType("byte[]", "[B"))
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
   * @return 类型映射表（key = JDBC 类型名，value = 方言类型描述）
   */
  public abstract Map<String, DialectType> getTypeMapping();

  /**
   * 根据 JDBC 类型名获取对应的短类型名。
   *
   * @param jdbcTypeName - JDBC 类型名（如 varchar, int4）
   * @return 短类型名，未找到默认 String
   */
  public String resolveShortType(String jdbcTypeName) {
    if (jdbcTypeName == null) {
      return "String";
    }
    DialectType type = getTypeMapping().get(jdbcTypeName.toLowerCase());
    return type != null ? type.getShortName() : "String";
  }

  /**
   * 根据 JDBC 类型名获取对应的全限定类型名。
   *
   * @param jdbcTypeName - JDBC 类型名
   * @return 全限定类型名，未找到默认 java.lang.String
   */
  public String resolveFullType(String jdbcTypeName) {
    if (jdbcTypeName == null) {
      return String.class.getName();
    }
    DialectType type = getTypeMapping().get(jdbcTypeName.toLowerCase());
    return type != null ? type.getFullName() : String.class.getName();
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
      if (lower.contains(":" + dialect.identifier + ":")
          || lower.startsWith("jdbc:" + dialect.identifier)) {
        return dialect;
      }
    }
    return POSTGRESQL;
  }

  /**
   * 数据库方言类型描述（短名 + 全名）。
   *
   * <p>封装 JDBC 类型名对应的 Java 短类型名和全限定类型名，
   * 用于代码生成的字段类型渲染。
   *
   * @author ydsz-team
   * @since 26.09.04
   */
  public static final class DialectType {
    /** 短类型名（如 String, Integer） */
    private final String shortName;
    /** 全限定类型名（如 java.lang.String） */
    private final String fullName;

    public DialectType(String shortName, String fullName) {
      this.shortName = shortName;
      this.fullName = fullName;
    }

    public String getShortName() {
      return shortName;
    }

    public String getFullName() {
      return fullName;
    }
  }
}
