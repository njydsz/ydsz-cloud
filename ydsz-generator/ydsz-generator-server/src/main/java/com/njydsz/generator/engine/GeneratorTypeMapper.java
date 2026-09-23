package com.njydsz.generator.engine;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据库 SQL 类型到编程语言的映射器。
 *
 * <p>读取配置中的 {@code generator.type-mapping} 表，将 JDBC 返回的数据库原生类型名
 * 转换为对应的 Java 类型全限定名或简单名。当配置中无匹配时使用
 * {@link #getDefaultMapping(String)} 兜底。
 *
 * <p>同时支持 Java 与 TypeScript 类型推断，供前后端代码生成模板使用。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "generator")
public class GeneratorTypeMapper {

  /** 类型映射表（key 为大写的 SQL 类型名，value 为 Java 类型全限定名或简单名）。 */
  private Map<String, String> typeMapping = new HashMap<>();

  /**
   * 根据数据库类型名推断 Java 类型。
   *
   * <p>查找逻辑：
   * <ol>
   *   <li>先尝试从配置映射表中精确匹配（大写 key）</li>
   *   <li>未命中时使用内置默认映射</li>
   *   <li>仍未命中则返回 {@code Object} 并打印警告日志</li>
   * </ol>
   *
   * @param dataType 数据库原生类型名（如 VARCHAR、BIGINT）
   * @return Java 类型字符串
   */
  public String resolveJavaType(String dataType) {
    if (dataType == null || dataType.isEmpty()) {
      return "Object";
    }
    String upperType = dataType.toUpperCase();
    // 优先配置映射
    String javaType = typeMapping.get(upperType);
    if (javaType != null) {
      return javaType;
    }
    // 内置兜底映射
    String fallback = getDefaultMapping(upperType);
    if (fallback != null) {
      return fallback;
    }
    log.warn("未知数据库类型 dataType={}，默认使用 Object", dataType);
    return "Object";
  }

  /**
   * 根据数据库类型名推断 TypeScript 类型。
   *
   * <p>映射规则：
   * <ul>
   *   <li>String/char/text 类型 → string</li>
   *   <li>数值类型（int/long/float/double/decimal）→ number</li>
   *   <li>Boolean 类型 → boolean</li>
   *   <li>日期类型（date/datetime/timestamp）→ string (ISO 8601)</li>
   *   <li>字节数组 → Blob</li>
   *   <li>其他 → unknown</li>
   * </ul>
   *
   * @param dataType 数据库原生类型名（如 VARCHAR、BIGINT）
   * @return TypeScript 类型字符串
   */
  public String resolveTsType(String dataType) {
    if (dataType == null || dataType.isEmpty()) {
      return "unknown";
    }
    switch (dataType.toUpperCase()) {
      case "VARCHAR":
      case "CHAR":
      case "TEXT":
      case "LONGTEXT":
      case "MEDIUMTEXT":
      case "TINYTEXT":
      case "CLOB":
      case "NVARCHAR":
      case "NCHAR":
      case "JSON":
        return "string";
      case "INT":
      case "INTEGER":
      case "TINYINT":
      case "SMALLINT":
      case "MEDIUMINT":
      case "BIGINT":
      case "FLOAT":
      case "DOUBLE":
      case "DECIMAL":
      case "NUMERIC":
        return "number";
      case "BOOLEAN":
      case "BOOL":
      case "BIT":
        return "boolean";
      case "DATE":
      case "DATETIME":
      case "TIMESTAMP":
      case "TIME":
        return "string";
      case "BLOB":
      case "LONGBLOB":
      case "MEDIUMBLOB":
      case "TINYBLOB":
      case "BYTEA":
      case "BINARY":
      case "VARBINARY":
        return "Blob";
      default:
        return "unknown";
    }
  }

  /**
   * 设置类型映射表。
   *
   * @param typeMapping 类型映射（key 自动转为大写）
   */
  public void setTypeMapping(Map<String, String> typeMapping) {
    this.typeMapping = typeMapping != null ? typeMapping : new HashMap<>();
  }

  /**
   * 获取当前类型映射表。
   *
   * @return 类型映射表（不可为 null）
   */
  public Map<String, String> getTypeMapping() {
    return typeMapping;
  }

  /**
   * 内置默认类型映射（当配置未覆盖时使用）。
   *
   * @param upperType 大写的数据库类型名
   * @return Java 类型，未知类型返回 null
   */
  private String getDefaultMapping(String upperType) {
    switch (upperType) {
      case "VARCHAR":
      case "CHAR":
      case "TEXT":
      case "LONGTEXT":
      case "MEDIUMTEXT":
      case "TINYTEXT":
      case "CLOB":
      case "NVARCHAR":
      case "NCHAR":
        return "String";
      case "INT":
      case "INTEGER":
      case "MEDIUMINT":
        return "Integer";
      case "TINYINT":
      case "SMALLINT":
        return "Integer";
      case "BIGINT":
        return "Long";
      case "FLOAT":
        return "Float";
      case "DOUBLE":
        return "Double";
      case "DECIMAL":
      case "NUMERIC":
        return "java.math.BigDecimal";
      case "DATE":
        return "java.time.LocalDate";
      case "DATETIME":
      case "TIMESTAMP":
        return "java.time.LocalDateTime";
      case "TIME":
        return "java.time.LocalTime";
      case "BOOLEAN":
      case "BOOL":
        return "Boolean";
      case "BIT":
        return "Boolean";
      case "BLOB":
      case "LONGBLOB":
      case "MEDIUMBLOB":
      case "TINYBLOB":
      case "BYTEA":
      case "BINARY":
      case "VARBINARY":
        return "[B";
      case "JSON":
        return "String";
      default:
        return null;
    }
  }
}
