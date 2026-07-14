package com.njydsz.pmis.common.jdbc.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import com.njydsz.pmis.common.json.YdszJson;

/**
 * List 类型 JSON 转换处理器
 *
 * <p>专门处理 List 类型的字段，自动将 List 对象序列化为 JSON 字符串存储到数据库，
 * 从数据库读取时将 JSON 字符串反序列化为 List 对象。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>存储动态配置项列表</li>
 *   <li>存储标签、分类等多值字段</li>
 *   <li>存储业务对象列表</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 实体类定义
 * public class Product {
 *     private Long id;
 *     private String name;
 *     private List&lt;String&gt; tags;  // 多标签
 *     private List&lt;Sku&gt; skus;      // SKU 列表
 * }
 *
 * // MyBatis 注解配置
 * {@code @TableName("product")}
 * public class Product {
 *     {@code @TableField(typeHandler = ListTypeHandler.class)}
 *     private List&lt;String&gt; tags;
 *
 *     {@code @TableField(typeHandler = JsonTypeHandler.class)}
 *     private List&lt;Sku&gt; skus;
 * }
 * </pre>
 *
 * <h2>数据库存储形式</h2>
 * <p>List 对象会被序列化为 JSON 数组字符串，例如：{@code ["tag1","tag2","tag3"]}</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 * @see JsonTypeHandler 通用 JSON 类型处理器
 */
public class ListTypeHandler extends BaseTypeHandler<List<Object>> {

    /**
     * 构造 List 类型处理器
     */
    public ListTypeHandler() {
    }

    /**
     * 设置非空参数，将 List 对象序列化为 JSON 字符串后设置到 PreparedStatement
     *
     * @param ps        PreparedStatement
     * @param i         参数索引
     * @param parameter 参数值
     * @param jdbcType  JDBC 类型
     * @throws SQLException 数据库异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Object> parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, YdszJson.toJson(parameter));
    }

    /**
     * 根据列名从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 List 对象
     *
     * @param rs         ResultSet
     * @param columnName 列名
     * @return 反序列化后的 List 对象
     * @throws SQLException 数据库异常
     */
    @Override
    public List<Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /**
     * 根据列索引从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 List 对象
     *
     * @param rs          ResultSet
     * @param columnIndex 列索引
     * @return 反序列化后的 List 对象
     * @throws SQLException 数据库异常
     */
    @Override
    public List<Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /**
     * 根据列索引从 CallableStatement 中获取可空结果，将 JSON 字符串反序列化为 List 对象
     *
     * @param cs          CallableStatement
     * @param columnIndex 列索引
     * @return 反序列化后的 List 对象
     * @throws SQLException 数据库异常
     */
    @Override
    public List<Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * 将 JSON 字符串反序列化为 List 对象
     * <p>通过泛型类型信息或 elementType 属性保留元素类型，避免 JSON 反序列化后元素变为 JSONObject。
     *
     * @param json JSON 字符串
     * @return 反序列化后的 List 对象，null 或空字符串时返回 null
     */
    private List<Object> parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return YdszJson.parseArray(json, Object.class);
    }
}