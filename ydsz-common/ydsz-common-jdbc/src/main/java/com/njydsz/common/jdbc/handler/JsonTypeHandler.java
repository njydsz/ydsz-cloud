package com.njydsz.common.jdbc.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import com.njydsz.common.json.YdszJson;
import lombok.extern.slf4j.Slf4j;

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
 *   <li><b>MySQL / Oracle / SQLServer（VARCHAR / TEXT / CLOB 列）</b>：使用 {@link
 *       PreparedStatement#setString(int, String)} 写入，兼容性最佳。
 *   <li><b>PostgreSQL（原生 JSON / JSONB 列）</b>：通过反射加载 PostgreSQL {@code PGobject} 并设置
 *       type 为 {@code jsonb}，使用 {@link PreparedStatement#setObject(int, Object)} 写入；
 *       根据 PGobject 内部类型列名完成二进制 JSON 处理，
 *       避免"column is of type jsonb but expression is of type character varying"错误。
 * </ul>
 *
 * <p><b>PostgreSQL 写入策略说明：</b>
 *
 * <ul>
 *   <li>当字段显式声明 {@code jdbcType=OTHER} 且驱动为 PostgreSQL 时，通过反射构造
 *       {@code org.postgresql.util.PGobject} 实例并设置其 type 为 {@code jsonb}
 *   <li>若 PostgreSQL 驱动不在 classpath（使用 MySQL/Oracle 等），
 *       降级为 {@code setString} 写入，由数据库隐式类型转换完成
 *   <li>YAML 中推荐配置：{@code jdbctype=OTHER} 确保 JSONB 类型列的正确写入
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
 * // MyBatis XML 映射配置（PostgreSQL JSONB 列推荐）
 * {@code <resultMap id="BaseResultMap" type="User">}
 *     {@code <result column="extra_info" property="extraInfo" jdbcType="OTHER" typeHandler="JsonTypeHandler"/>}
 * {@code < /resultMap>}
 *
 * // MyBatis 注解配置
 * {@code @Results({
 *     @Result(column = "extra_info", property = "extraInfo", jdbcType = JdbcType.OTHER,
 *             typeHandler = JsonTypeHandler.class)
 * })}
 * </pre>
 *
 * <h2>数据库字段要求</h2>
 *
 * <p>对应的数据库字段类型应为 VARCHAR、TEXT、JSON 或 JSONB（PostgreSQL 原生支持）。
 *
 * @param <T> Java 对象类型
 * @author ydsz-team
 * @since 26.09.01
 * @see <a href="https://mybatis.org/mybatis-3/zh/configuration.html#typeHandlers">MyBatis
 *     TypeHandler</a>
 */
@Slf4j
public class JsonTypeHandler<T> extends BaseTypeHandler<T> {

  private static final String PG_JSONB_TYPE = "jsonb";
  private static final String PG_JSON_TYPE = "json";

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
   * 设置非空参数，将 Java 对象序列化为 JSON 后设置到 PreparedStatement。
   *
   * <p>数据库兼容策略：
   *
   * <ul>
   *   <li>jdbcType = OTHER 且 PostgreSQL 驱动可用：构造 {@link PGobject}（type=jsonb），
   *       通过 {@code setObject(...)} 写入，保证 PostgreSQL JSONB 列的二进制处理
   *   <li>其他情况：使用 {@code setString} 写入（MySQL / Oracle / SQLServer / 文本 json 列）
   * </ul>
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
      // PostgreSQL 原生 JSONB 写入：使用 PGobject 显式声明类型
      writePostgresJsonb(ps, i, json);
    } else {
      // MySQL / Oracle / SQLServer 等：VARCHAR / TEXT / CLOB 列
      ps.setString(i, json);
    }
  }

  /**
   * 使用反射构造 PostgreSQL PGobject 写入 JSONB 列。
   *
   * <p>由于 PostgreSQL 驱动为 optional+runtime scope，编译期 {@code org.postgresql.util.PGobject}
   * 不可直接引用。通过反射加载 PGobject 类，避免编译期依赖。
   *
   * <p>若 PostgreSQL 驱动不可用（非 PG 数据源）或构造失败，降级为 setString，
   * 由数据库隐式类型转换完成写入。
   *
   * @param ps PreparedStatement
   * @param i 参数索引
   * @param json JSON 字符串
   * @throws SQLException 写入异常
   */
  private void writePostgresJsonb(PreparedStatement ps, int i, String json) throws SQLException {
    try {
      // 反射加载 PostgreSQL PGobject（避免编译期依赖 optional+runtime 的 PG 驱动）
      Class<?> pgObjectClass = Class.forName("org.postgresql.util.PGobject");
      Object pgObject = pgObjectClass.getDeclaredConstructor().newInstance();
      pgObjectClass.getMethod("setType", String.class).invoke(pgObject, PG_JSONB_TYPE);
      pgObjectClass.getMethod("setValue", String.class).invoke(pgObject, json);
      ps.setObject(i, pgObject);
    } catch (ClassNotFoundException e) {
      // PostgreSQL 驱动不在 classpath（使用 MySQL/Oracle 等）
      log.debug("JsonTypeHandler: PostgreSQL 驱动不可用，降级为 setString");
      ps.setString(i, json);
    } catch (Exception e) {
      // PGobject 构造或设置失败，降级为 setString
      log.debug(
          "JsonTypeHandler: PGobject 写入失败，降级为 setString（reason: {}）",
          e.getMessage());
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
