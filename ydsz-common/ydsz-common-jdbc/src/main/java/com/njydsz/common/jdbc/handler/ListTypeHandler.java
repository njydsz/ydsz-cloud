package com.njydsz.common.jdbc.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.parser.JsonParserUtil;

/**
 * List 类型 JSON 转换处理器
 *
 * <p>专门处理 List 类型的字段，自动将 List 对象序列化为 JSON 字符串存储到数据库， 从数据库读取时将 JSON 字符串反序列化为 List 对象。
 *
 * <p>通过 {@code elementType} 构造参数保留具体元素类型，解决泛型擦除导致的 反序列化后元素变为 {@code LinkedHashMap} 的问题。无参构造
 * fallback 到 {@link Object.class}， 保持向后兼容（行为与旧版一致）。
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li>存储动态配置项列表
 *   <li>存储标签、分类等多值字段
 *   <li>存储业务对象列表
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>
 * // 实体类定义
 * public class Product {
 *     private Long id;
 *     private String name;
 *     private List&lt;String&gt; tags;  // 多标签
 *     private List&lt;Sku&gt; skus;      // SKU 列表
 * }
 *
 * // MyBatis 注解配置 —— 简单类型（无参构造即可）
 * {@code @TableName("product")}
 * public class Product {
 *     {@code @TableField(typeHandler = ListTypeHandler.class)}
 *     private List&lt;String&gt; tags;
 * }
 *
 * // MyBatis XML 配置 —— 复杂类型（需指定 elementType，确保反序列化为目标类型）
 * {@code <resultMap id="BaseResultMap" type="Product">}
 *     {@code <result column="skus" property="skus" javaType="java.util.List"}
 *                  typeHandler="com.njydsz.common.jdbc.handler.ListTypeHandler"/>
 * {@code < /resultMap>}
 *
 * // 业务代码手动注册有参构造处理器（推荐用于 List&lt;&gt; 复杂对象场景）
 * {@code
 *   // 在配置类或初始化阶段注册
 *   configuration.getTypeHandlerRegistry().register(
 *       List.class, new ListTypeHandler<>(Sku.class));
 * }
 * </pre>
 *
 * <h2>数据库存储形式</h2>
 *
 * <p>List 对象会被序列化为 JSON 数组字符串，例如：{@code ["tag1","tag2","tag3"]}
 *
 * @param <E> List 元素类型
 * @author ydsz-team
 * @since 26.09.01
 * @see JsonTypeHandler 通用 JSON 类型处理器
 */
public class ListTypeHandler<E> extends BaseTypeHandler<List<E>> {

  /** 列表元素类型（运行时保留，避免泛型擦除） */
  private final Class<E> elementType;

  /**
   * 构造 List 类型处理器（指定元素类型）。
   *
   * <p>反序列化时会将 JSON 数组元素转换为 {@code elementType} 指定的类型， 而非默认的 {@code LinkedHashMap}。
   *
   * @param elementType 列表元素类型，不可为 null
   * @throws NullPointerException 如果 elementType 为 null
   */
  public ListTypeHandler(Class<E> elementType) {
    if (elementType == null) {
      throw new NullPointerException("elementType cannot be null");
    }
    this.elementType = elementType;
  }

  /**
   * 无参构造（MyBatis 反射实例化兜底）。
   *
   * <p>当通过 {@code @TableField(typeHandler = ListTypeHandler.class)} 等方式使用时， MyBatis
   * 通过反射调用无参构造，此时元素类型 fallback 到 {@link Object.class}， 反序列化行为与旧版一致（元素为 LinkedHashMap / 基本类型）。
   */
  public ListTypeHandler() {
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
   * 设置非空参数，将 List 对象序列化为 JSON 字符串后设置到 PreparedStatement
   *
   * @param ps PreparedStatement
   * @param i 参数索引
   * @param parameter 参数值
   * @param jdbcType JDBC 类型
   * @throws SQLException 数据库异常
   */
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, List<E> parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, YdszJson.toJson(parameter));
  }

  /**
   * 根据列名从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 List 对象
   *
   * @param rs ResultSet
   * @param columnName 列名
   * @return 反序列化后的 List 对象
   * @throws SQLException 数据库异常
   */
  @Override
  public List<E> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  /**
   * 根据列索引从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 List 对象
   *
   * @param rs ResultSet
   * @param columnIndex 列索引
   * @return 反序列化后的 List 对象
   * @throws SQLException 数据库异常
   */
  @Override
  public List<E> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  /**
   * 根据列索引从 CallableStatement 中获取可空结果，将 JSON 字符串反序列化为 List 对象
   *
   * @param cs CallableStatement
   * @param columnIndex 列索引
   * @return 反序列化后的 List 对象
   * @throws SQLException 数据库异常
   */
  @Override
  public List<E> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  /**
   * 将 JSON 字符串反序列化为 List 对象。
   *
   * <p>当 elementType 为 {@link Object.class}（无参构造 fallback）时， 使用 {@link
   * JsonParserUtil#parseArray(String)} 保持旧版行为； 当指定了具体 elementType 时，使用 {@link
   * YdszJson#parseArray(String, Class)} 将元素转换为目标类型。
   *
   * @param json JSON 字符串
   * @return 反序列化后的 List 对象，null 或空字符串时返回 null
   */
  private List<E> parse(String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    if (elementType == Object.class) {
      // 向后兼容：无参构造 fallback，行为与旧版一致
      // JsonParserUtil.parseArray 返回原始 List，无法在编译期验证泛型；仅在后端兼容路径使用，类型安全由调用方保证
      List<E> result = (List<E>) JsonParserUtil.parseArray(json);
      return result;
    }
    // 有参构造：使用 YdszJson 类型安全反序列化，确保元素类型正确
    return YdszJson.parseArray(json, elementType);
  }
}
