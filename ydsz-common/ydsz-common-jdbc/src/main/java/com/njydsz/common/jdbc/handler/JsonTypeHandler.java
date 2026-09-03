package com.njydsz.common.jdbc.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import com.njydsz.common.json.YdszJson;

/**
 * JSON 类型转换处理器
 *
 * <p>实现 MyBatis {@link BaseTypeHandler} 接口，提供 Java 对象与 JSON 字符串之间的双向转换能力。 底层使用项目统一的 {@link
 * YdszJson} 引擎（零外部 JSON 库依赖），替代 MyBatis-Plus 自带的 {@code JacksonTypeHandler}，避免引入 Jackson
 * 运行时依赖，保证全链路 JSON 引擎一致性。
 *
 * <h2>数据库兼容性</h2>
 *
 * <ul>
 *   <li><b>MySQL / Oracle / SQLServer / 等（VARCHAR / TEXT / CLOB 列）</b>：使用 {@link
 *       PreparedStatement#setString(int, String)} 写入，兼容性最佳。
 *   <li><b>PostgreSQL（原生 JSON / JSONB 列）</b>：当字段显式声明 {@code jdbcType=OTHER} 时使用 {@link
 *       PreparedStatement#setObject(int, Object, int)} 配合 {@link Types#OTHER}， 由 PostgreSQL 驱动完成二进制
 *       JSON 处理。
 * </ul>
 *
 * <h2>支持的类型</h2>
 *
 * <ul>
 *   <li>普通 JavaBean
 *   <li>List&lt;T&gt; 集合
 *   <li>Map&lt;String, T&gt; 映射
 *   <li>其他可序列化的对象
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>
 * // MyBatis XML 映射配置
 * {@code <resultMap id="BaseResultMap" type="User">}
 *     {@code <result column="extra_info" property="extraInfo" jdbcType="VARCHAR" typeHandler="JsonTypeHandler"/>}
 * {@code < /resultMap>}
 *
 * // MyBatis 注解配置
 * {@code @Results({
 *     @Result(column = "extra_info", property = "extraInfo", typeHandler = JsonTypeHandler.class)
 * })}
 * </pre>
 *
 * <h2>数据库字段要求</h2>
 *
 * <p>对应的数据库字段类型应为 VARCHAR、TEXT、JSON 或 JSONB（PostgreSQL 原生支持）。
 *
 * <p>对于 PostgreSQL JSONB 列，使用 {@code Types.OTHER} 设置参数，确保驱动正确处理二进制 JSON。
 *
 * @param <T> Java 对象类型
 * @author ydsz-team
 * @since 26.09.01
 * @see <a href="https://mybatis.org/mybatis-3/zh/configuration.html#typeHandlers">MyBatis
 *     TypeHandler</a>
 */
public class JsonTypeHandler<T> extends BaseTypeHandler<T> {

  private final Class<T> type;

  /**
   * 构造 JSON 类型处理器
   *
   * @param type 目标类型 Class
   */
  public JsonTypeHandler(Class<T> type) {
    if (type == null) {
      throw new NullPointerException("Type argument cannot be null");
    }
    this.type = type;
  }

  /**
   * 无参构造（MyBatis 实例化兜底）。
   *
   * <p>当字段类型无法在注册阶段解析时，MyBatis 可能通过无参构造创建处理器， 此时默认以 {@code Object.class} 反序列化（运行时再按字段类型擦除处理）。
   */
  public JsonTypeHandler() {
    this(rawClass(Object.class));
  }

  /**
   * 将 Class<?> 转换为 Class<T>。
   *
   * <p>这是一个常见的类型安全转换模式：Class 的泛型参数在运行时是协变的，
   * 且 Class 实例本身不包含泛型类型的运行时信息，因此该转换在逻辑上是安全的。
   * 无参构造中调用此方法时，目标类型 T 已被擦除为 Object，与传入的 Object.class 一致。
   *
   * @param clazz 原始 Class 对象
   * @param <T> 目标泛型类型
   * @return 转换后的 Class 对象
   */
  private static <T> Class<T> rawClass(Class<?> clazz) {
    return (Class<T>) clazz;
  }

  /**
   * 设置非空参数，将 Java 对象序列化为 JSON 字符串后设置到 PreparedStatement。
   *
   * <p>数据库兼容策略：当 jdbcType 为字符串类（VARCHAR / CHAR / CLOB 等）或未指定时， 使用 {@code setString}（MySQL / Oracle
   * / SQLServer 等最常见场景，兼容性最佳）； 仅当显式声明为 {@link JdbcType#OTHER}（PostgreSQL JSON / JSONB 原生列）时， 使用
   * {@code setObject(..., Types.OTHER)} 交由驱动处理二进制 JSON。
   *
   * @param ps PreparedStatement
   * @param i 参数索引
   * @param parameter 参数值
   * @param jdbcType JDBC 类型
   * @throws SQLException 数据库异常
   */
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
      throws SQLException {
    String json = toJsonString(parameter);
    if (jdbcType == JdbcType.OTHER) {
      // PostgreSQL 原生 JSON / JSONB 列
      ps.setObject(i, json, Types.OTHER);
    } else {
      // MySQL / Oracle / SQLServer 等：VARCHAR / TEXT / CLOB 列（兼容 PostgreSQL 文本/json 列）
      ps.setString(i, json);
    }
  }

  /**
   * 根据列名从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 Java 对象
   *
   * @param rs ResultSet
   * @param columnName 列名
   * @return 反序列化后的 Java 对象
   * @throws SQLException 数据库异常
   */
  @Override
  public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  /**
   * 根据列索引从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 Java 对象
   *
   * @param rs ResultSet
   * @param columnIndex 列索引
   * @return 反序列化后的 Java 对象
   * @throws SQLException 数据库异常
   */
  @Override
  public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  /**
   * 根据列索引从 CallableStatement 中获取可空结果，将 JSON 字符串反序列化为 Java 对象
   *
   * @param cs CallableStatement
   * @param columnIndex 列索引
   * @return 反序列化后的 Java 对象
   * @throws SQLException 数据库异常
   */
  @Override
  public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  /**
   * 将对象序列化为 JSON 字符串
   *
   * @param parameter 待序列化对象
   * @return JSON 字符串，null 时返回 null
   */
  private String toJsonString(Object parameter) {
    if (parameter == null) {
      return null;
    }
    return YdszJson.toJson(parameter);
  }

  /**
   * 将 JSON 字符串反序列化为对象
   *
   * @param json JSON 字符串
   * @return 反序列化后的对象，null 或空字符串时返回 null
   */
  private T parse(String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    // YdszJson.fromJson 返回 type 指定的类型，与 T 一致（由构造参数保证）
    return YdszJson.fromJson(json, type);
  }
}
