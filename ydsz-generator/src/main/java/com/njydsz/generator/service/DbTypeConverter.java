package com.njydsz.generator.service;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import com.njydsz.generator.config.DatabaseDialect;

/**
 * 数据库 JDBC 类型 → Java 类型映射器。
 *
 * <p>将 JDBC {@link java.sql.Types} 的整型码或 JDBC 类型名映射到对应 Java 类型的短名和全名，
 * 用于代码生成的字段类型渲染。
 *
 * <p>支持多数据库方言：通过 {@link DatabaseDialect} 根据 JDBC URL 自动识别数据库类型，
 * 并使用对应的类型映射规则。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DbTypeConverter {

  /** jdbcType -> [shortType, fullType]（通用映射，适用于所有数据库） */
  private static final Map<Integer, String[]> TYPE_MAP = new HashMap<>(32);

  /** 当前激活的数据库方言 */
  private static volatile DatabaseDialect currentDialect = DatabaseDialect.POSTGRESQL;

  static {
    // String 类
    TYPE_MAP.put(Types.VARCHAR, new String[]{"String", "java.lang.String"});
    TYPE_MAP.put(Types.CHAR, new String[]{"String", "java.lang.String"});
    TYPE_MAP.put(Types.LONGVARCHAR, new String[]{"String", "java.lang.String"});
    TYPE_MAP.put(Types.NVARCHAR, new String[]{"String", "java.lang.String"});
    TYPE_MAP.put(Types.NCHAR, new String[]{"String", "java.lang.String"});
    TYPE_MAP.put(Types.LONGNVARCHAR, new String[]{"String", "java.lang.String"});
    TYPE_MAP.put(Types.CLOB, new String[]{"String", "java.lang.String"});
    TYPE_MAP.put(Types.NCLOB, new String[]{"String", "java.lang.String"});
    TYPE_MAP.put(Types.SQLXML, new String[]{"String", "java.lang.String"});
    // Integer / Long 类
    TYPE_MAP.put(Types.INTEGER, new String[]{"Integer", "java.lang.Integer"});
    TYPE_MAP.put(Types.SMALLINT, new String[]{"Integer", "java.lang.Integer"});
    TYPE_MAP.put(Types.TINYINT, new String[]{"Integer", "java.lang.Integer"});
    TYPE_MAP.put(Types.BIGINT, new String[]{"Long", "java.lang.Long"});
    // Decimal 类（金融计算用 BigDecimal）
    TYPE_MAP.put(Types.NUMERIC, new String[]{"BigDecimal", "java.math.BigDecimal"});
    TYPE_MAP.put(Types.DECIMAL, new String[]{"BigDecimal", "java.math.BigDecimal"});
    // Boolean 类
    TYPE_MAP.put(Types.BOOLEAN, new String[]{"Boolean", "java.lang.Boolean"});
    TYPE_MAP.put(Types.BIT, new String[]{"Boolean", "java.lang.Boolean"});
    // Date 类
    TYPE_MAP.put(Types.TIMESTAMP, new String[]{"LocalDateTime", "java.time.LocalDateTime"});
    TYPE_MAP.put(Types.TIMESTAMP_WITH_TIMEZONE, new String[]{"LocalDateTime", "java.time.LocalDateTime"});
    TYPE_MAP.put(Types.DATE, new String[]{"LocalDate", "java.time.LocalDate"});
    TYPE_MAP.put(Types.TIME, new String[]{"LocalDateTime", "java.time.LocalDateTime"});
  }

  private DbTypeConverter() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 根据 JDBC URL 设置当前数据库方言。
   *
   * @param jdbcUrl - JDBC 连接 URL
   */
  public static void setDialectByUrl(String jdbcUrl) {
    currentDialect = DatabaseDialect.fromJdbcUrl(jdbcUrl);
  }

  /**
   * 获取当前激活的数据库方言。
   *
   * @return 当前方言
   */
  public static DatabaseDialect getCurrentDialect() {
    return currentDialect;
  }

  /**
   * 将 JDBC 类型码映射为 Java 短类型名。
   *
   * <p>未识别的类型统一降级为 {@code String}（与 {@code varchar} 兼容），确保生成不中断。
   *
   * @param jdbcType - JDBC 类型码（{@link java.sql.Types}）
   * @return Java 短类型名
   */
  public static String toJavaShortType(int jdbcType) {
    String[] pair = TYPE_MAP.get(jdbcType);
    return pair != null ? pair[0] : "String";
  }

  /**
   * 将 JDBC 类型码映射为 Java 全限定类型名。
   *
   * @param jdbcType - JDBC 类型码
   * @return Java 全限定类型名
   */
  public static String toJavaType(int jdbcType) {
    String[] pair = TYPE_MAP.get(jdbcType);
    return pair != null ? pair[1] : "java.lang.String";
  }

  /**
   * 根据数据库方言的 JDBC 类型名解析 Java 短类型名。
   *
   * <p>用于处理数据库特有的类型名（如 PostgreSQL 的 {@code int4/int8/bool}，MySQL 的 {@code datetime/tinyint}）。
   *
   * @param jdbcTypeName - JDBC 类型名（如 {@code int4}、{@code varchar2}）
   * @return Java 短类型名
   */
  public static String toJavaShortTypeByName(String jdbcTypeName) {
    return currentDialect.resolveJavaType(jdbcTypeName)[0];
  }

  /**
   * 根据数据库方言的 JDBC 类型名解析 Java 全限定类型名。
   *
   * @param jdbcTypeName - JDBC 类型名
   * @return Java 全限定类型名
   */
  public static String toJavaTypeByName(String jdbcTypeName) {
    return currentDialect.resolveJavaType(jdbcTypeName)[1];
  }

  /**
   * 判断字段是否为需要 {@code java.time.*} 包导入的时间类型。
   *
   * @param jdbcType - JDBC 类型码
   * @return true 表示需要 import java.time.LocalDateTime/LocalDate
   */
  public static boolean isDateTimeType(int jdbcType) {
    return jdbcType == Types.TIMESTAMP
        || jdbcType == Types.TIMESTAMP_WITH_TIMEZONE
        || jdbcType == Types.DATE
        || jdbcType == Types.TIME;
  }

  /**
   * 判断字段是否为需要 {@code java.math.BigDecimal} 包导入的精确数值类型。
   *
   * @param jdbcType - JDBC 类型码
   * @return true 表示需要 import java.math.BigDecimal
   */
  public static boolean isDecimalType(int jdbcType) {
    return jdbcType == Types.NUMERIC || jdbcType == Types.DECIMAL;
  }
}
