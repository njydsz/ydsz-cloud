package com.njydsz.pmis.common.jdbc.handler;

import com.njydsz.pmis.common.util.JsonUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * Map 类型 JSON 转换处理器
 *
 * <p>专门处理 Map 类型的字段，自动将 Map 对象序列化为 JSON 字符串存储到数据库，
 * 从数据库读取时将 JSON 字符串反序列化为 Map 对象。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>存储动态键值对配置</li>
 *   <li>存储业务扩展属性</li>
 *   <li>存储稀疏数据字段</li>
 * </ul>
 *
 * <h2>使用示例</h2>
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
 * // MyBatis XML 配置
 * {@code <resultMap id="BaseResultMap" type="SystemConfig">}
 *     {@code <result column="extra_params" property="extraParams" typeHandler="MapTypeHandler"/>}
 * {@code </resultMap>}
 * </pre>
 *
 * <h2>数据库存储形式</h2>
 * <p>Map 对象会被序列化为 JSON 对象字符串，例如：{@code {"key1":"value1","key2":123}}</p>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 * @see JsonTypeHandler 通用 JSON 类型处理器
 */
public class MapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    /**
     * 构造 Map 类型处理器
     */
    public MapTypeHandler() {
    }

    /**
     * 设置非空参数，将 Map 对象序列化为 JSON 字符串后设置到 PreparedStatement
     *
     * @param ps        PreparedStatement
     * @param i         参数索引
     * @param parameter 参数值
     * @param jdbcType  JDBC 类型
     * @throws SQLException 数据库异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, JsonUtils.toJson(parameter));
    }

    /**
     * 根据列名从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 Map 对象
     *
     * @param rs         ResultSet
     * @param columnName 列名
     * @return 反序列化后的 Map 对象
     * @throws SQLException 数据库异常
     */
    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /**
     * 根据列索引从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 Map 对象
     *
     * @param rs          ResultSet
     * @param columnIndex 列索引
     * @return 反序列化后的 Map 对象
     * @throws SQLException 数据库异常
     */
    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /**
     * 根据列索引从 CallableStatement 中获取可空结果，将 JSON 字符串反序列化为 Map 对象
     *
     * @param cs          CallableStatement
     * @param columnIndex 列索引
     * @return 反序列化后的 Map 对象
     * @throws SQLException 数据库异常
     */
    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * 将 JSON 字符串反序列化为 Map 对象
     * <p>通过泛型类型信息或 valueType 属性保留值类型，避免 JSON 反序列化后值变为 JSONObject。
     *
     * @param json JSON 字符串
     * @return 反序列化后的 Map 对象，null 或空字符串时返回 null
     */
    private Map<String, Object> parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JsonUtils.parseMap(json);
    }
}
