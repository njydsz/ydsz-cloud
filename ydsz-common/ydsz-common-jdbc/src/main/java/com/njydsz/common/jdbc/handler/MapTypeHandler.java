package com.njydsz.common.jdbc.handler;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.parser.JsonParserUtil;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Map 类型 JSON 转换处理器
 *
 * <p>专门处理 Map 类型的字段，自动将 Map 对象序列化为 JSON 字符串存储到数据库， 从数据库读取时将 JSON 字符串反序列化为 Map 对象。
 *
 * <p>通过 {@code valueType} 构造参数保留具体值类型，解决泛型擦除导致的 反序列化后值变为 {@code LinkedHashMap} 的问题。无参构造 fallback 到
 * {@link Object.class}， 保持向后兼容（行为与旧版一致）。
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li>存储动态键值对配置
 *   <li>存储业务扩展属性
 *   <li>存储稀疏数据字段
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>
 * // 实体类定义
 * public class SystemConfig {
 *     private Long id;
 *     private String configKey;
 *
 *     // 存储动态配置项，如 {"timeout": 30, "retryCount": 3}
 *     {@code @TableField(typeHandler = MapTypeHandler.class)}
 *     private Map&lt;String, Object&gt; extraParams;
 * }
 *
 * // MyBatis XML 配置 —— 复杂值类型（需通过有参构造指定 valueType）
 * {@code <resultMap id="BaseResultMap" type="SystemConfig">}
 *     {@code <result column="extra_params" property="extraParams"}
 *                  typeHandler="com.njydsz.common.jdbc.handler.MapTypeHandler"/>
 * {@code </resultMap>}
 *
 * // 业务代码手动注册有参构造处理器（推荐用于 Map&lt;String, ?&gt; 复杂值场景）
 * {@code
 *   // 在配置类或初始化阶段注册
 *   configuration.getTypeHandlerRegistry().register(
 *       Map.class, new MapTypeHandler&lt;&gt;(Sku.class));
 * }
 * </pre>
 *
 * <h2>数据库存储形式</h2>
 *
 * <p>Map 对象会被序列化为 JSON 对象字符串，例如：{@code {"key1":"value1","key2":123}}
 *
 * @param <V> Map 值类型
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonTypeHandler 通用 JSON 类型处理器
 */
public class MapTypeHandler<V> extends BaseTypeHandler<Map<String, V>> {

  /** 映射值类型（运行时保留，避免泛型擦除） */
  private final Class<V> valueType;

  /**
   * 构造 Map 类型处理器（指定值类型）。
   *
   * <p>反序列化时会将 JSON 对象值转换为 {@code valueType} 指定的类型， 而非默认的 {@code LinkedHashMap}。
   *
   * @param valueType Map 值类型，不可为 null
   * @throws NullPointerException 如果 valueType 为 null
   */
  public MapTypeHandler(Class<V> valueType) {
    if (valueType == null) {
      throw new NullPointerException("valueType cannot be null");
    }
    this.valueType = valueType;
  }

  /**
   * 无参构造（MyBatis 反射实例化兜底）。
   *
   * <p>当通过 {@code @TableField(typeHandler = MapTypeHandler.class)} 等方式使用时， MyBatis 通过反射调用无参构造，此时值类型
   * fallback 到 {@link Object.class}， 反序列化行为与旧版一致（值为 LinkedHashMap / 基本类型）。
   */
  @SuppressWarnings("unchecked")
  public MapTypeHandler() {
    this((Class<V>) Object.class);
  }

  /**
   * 设置非空参数，将 Map 对象序列化为 JSON 字符串后设置到 PreparedStatement
   *
   * @param ps PreparedStatement
   * @param i 参数索引
   * @param parameter 参数值
   * @param jdbcType JDBC 类型
   * @throws SQLException 数据库异常
   */
  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, Map<String, V> parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, YdszJson.toJson(parameter));
  }

  /**
   * 根据列名从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 Map 对象
   *
   * @param rs ResultSet
   * @param columnName 列名
   * @return 反序列化后的 Map 对象
   * @throws SQLException 数据库异常
   */
  @Override
  public Map<String, V> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  /**
   * 根据列索引从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 Map 对象
   *
   * @param rs ResultSet
   * @param columnIndex 列索引
   * @return 反序列化后的 Map 对象
   * @throws SQLException 数据库异常
   */
  @Override
  public Map<String, V> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  /**
   * 根据列索引从 CallableStatement 中获取可空结果，将 JSON 字符串反序列化为 Map 对象
   *
   * @param cs CallableStatement
   * @param columnIndex 列索引
   * @return 反序列化后的 Map 对象
   * @throws SQLException 数据库异常
   */
  @Override
  public Map<String, V> getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  /**
   * 将 JSON 字符串反序列化为 Map 对象。
   *
   * <p>当 valueType 为 {@link Object.class}（无参构造 fallback）时， 使用 {@link
   * JsonParserUtil#parseObject(String)} 保持旧版行为； 当指定了具体 valueType 时，使用 {@link
   * YdszJson#fromJsonToMap(String, Class, Class)} 将值转换为目标类型。
   *
   * @param json JSON 字符串
   * @return 反序列化后的 Map 对象，null 或空字符串时返回 null
   */
  @SuppressWarnings("unchecked")
  private Map<String, V> parse(String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    if (valueType == Object.class) {
      // 向后兼容：无参构造 fallback，行为与旧版一致
      return (Map<String, V>) JsonParserUtil.parseObject(json);
    }
    // 有参构造：使用 YdszJson 类型安全反序列化，确保值类型正确
    return YdszJson.fromJsonToMap(json, String.class, valueType);
  }
}
