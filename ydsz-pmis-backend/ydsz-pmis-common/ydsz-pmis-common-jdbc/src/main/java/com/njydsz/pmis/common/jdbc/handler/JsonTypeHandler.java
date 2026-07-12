package com.njydsz.pmis.common.jdbc.handler;

import com.njydsz.pmis.common.util.JsonUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JSON 类型转换处理器
 *
 * <p>实现 MyBatis TypeHandler 接口，提供 Java 对象与 JSON 字符串之间的双向转换能力。
 * 使用 Jackson 进行序列化/反序列化，支持复杂对象的存储。</p>
 *
 * <h2>支持的类型</h2>
 * <ul>
 *   <li>普通 JavaBean</li>
 *   <li>List&lt;T&gt; 集合</li>
 *   <li>Map&lt;String, T&gt; 映射</li>
 *   <li>其他可序列化的对象</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // MyBatis XML 映射配置
 * {@code <resultMap id="BaseResultMap" type="User">}
 *     {@code <result column="extra_info" property="extraInfo" jdbcType="VARCHAR" typeHandler="JsonTypeHandler"/>}
 * {@code </resultMap>}
 *
 * // MyBatis 注解配置
 * {@code @Results({
 *     @Result(column = "extra_info", property = "extraInfo", typeHandler = JsonTypeHandler.class)
 * })}
 * </pre>
 *
 * <h2>数据库字段要求</h2>
 * <p>对应的数据库字段类型应为 VARCHAR、TEXT 或其他文本类型。</p>
 *
 * @param <T> Java 对象类型
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 * @see <a href="https://mybatis.org/mybatis-3/zh/configuration.html#typeHandlers">MyBatis TypeHandler</a>
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
     * 设置非空参数，将 Java 对象序列化为 JSON 字符串后设置到 PreparedStatement
     *
     * @param ps        PreparedStatement
     * @param i         参数索引
     * @param parameter 参数值
     * @param jdbcType  JDBC 类型
     * @throws SQLException 数据库异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, toJsonString(parameter));
    }

    /**
     * 根据列名从 ResultSet 中获取可空结果，将 JSON 字符串反序列化为 Java 对象
     *
     * @param rs         ResultSet
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
     * @param rs          ResultSet
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
     * @param cs          CallableStatement
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
        return JsonUtils.toJson(parameter);
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
        return JsonUtils.parseObject(json, type);
    }
}