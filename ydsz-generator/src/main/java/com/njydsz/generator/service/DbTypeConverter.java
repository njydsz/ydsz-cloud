package com.njydsz.generator.service;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

  /** jdbcType -> DialectType（通用映射，适用于所有数据库） */
  private static final Map<Integer, DatabaseDialect.DialectType> TYPE_MAP = new HashMap<>(32);

  /** 当前激活的数据库方言 */
  private static volatile DatabaseDialect currentDialect = DatabaseDialect.POSTGRESQL;

  static {
    // String 类
    TYPE_MAP.put(Types.VARCHAR, dt(String.class));
    TYPE_MAP.put(Types.CHAR, dt(String.class));
    TYPE_MAP.put(Types.LONGVARCHAR, dt(String.class));
    TYPE_MAP.put(Types.NVARCHAR, dt(String.class));
    TYPE_MAP.put(Types.NCHAR, dt(String.class));
    TYPE_MAP.put(Types.LONGNVARCHAR, dt(String.class));
    TYPE_MAP.put(Types.CLOB, dt(String.class));
    TYPE_MAP.put(Types.NCLOB, dt(String.class));
    TYPE_MAP.put(Types.SQLXML, dt(String.class));
    // Integer / Long 类
    TYPE_MAP.put(Types.INTEGER, dt(Integer.class));
    TYPE_MAP.put(Types.SMALLINT, dt(Integer.class));
    TYPE_MAP.put(Types.TINYINT, dt(Integer.class));
    TYPE_MAP.put(Types.BIGINT, dt(Long.class));
    // Decimal 类（金融计算用 BigDecimal）
    TYPE_MAP.put(Types.NUMERIC, dt(BigDecimal.class));
    TYPE_MAP.put(Types.DECIMAL, dt(BigDecimal.class));
    // Boolean 类
    TYPE_MAP.put(Types.BOOLEAN, dt(Boolean.class));
    TYPE_MAP.put(Types.BIT, dt(Boolean.class));
    // Date 类
    TYPE_MAP.put(Types.TIMESTAMP, dt(LocalDateTime.class));
    TYPE_MAP.put(Types.TIMESTAMP_WITH_TIMEZONE, dt(LocalDateTime.class));
    TYPE_MAP.put(Types.DATE, dt(LocalDate.class));
    TYPE_MAP.put(Types.TIME, dt(LocalDateTime.class));
  }

  private DbTypeConverter() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 根据 Class 对象创建 DialectType 实例。
   *
   * @param clazz - Java 类型
   * @return DialectType 描述
   */
  private static DatabaseDialect.DialectType dt(Class<?> clazz) {
    return new DatabaseDialect.DialectType(
        clazz.getSimpleName(),
        clazz.getName()
    );
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
    DatabaseDialect.DialectType type = TYPE_MAP.get(jdbcType);
    return type != null ? type.getShortName() : "String";
  }

  /**
   * 将 JDBC 类型码映射为 Java 全限定类型名。
   *
   * @param jdbcType - JDBC 类型码
   * @return Java 全限定类型名
   */
  public static String toJavaType(int jdbcType) {
    DatabaseDialect.DialectType type = TYPE_MAP.get(jdbcType);
    return type != null ? type.getFullName() : String.class.getName();
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
    return currentDialect.resolveShortType(jdbcTypeName);
  }

  /**
   * 根据数据库方言的 JDBC 类型名解析 Java 全限定类型名。
   *
   * @param jdbcTypeName - JDBC 类型名
   * @return Java 全限定类型名
   */
  public static String toJavaTypeByName(String jdbcTypeName) {
    return currentDialect.resolveFullType(jdbcTypeName);
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
