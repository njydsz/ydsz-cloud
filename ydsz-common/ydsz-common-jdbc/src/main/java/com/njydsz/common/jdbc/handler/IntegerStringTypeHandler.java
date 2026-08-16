package com.njydsz.common.jdbc.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 整数列与字符串 Java 属性的类型转换处理器。
 *
 * <p>用于兼容遗留表中使用整数存储状态码（如 {@code 0/1}），但业务实体统一使用 {@link String} 类型的场景。读取时将 {@code INTEGER}
 * 列转为字符串，写入时将字符串 解析为整数。
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @TableField(value = "status", typeHandler = IntegerStringTypeHandler.class)
 * private String status;
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class IntegerStringTypeHandler extends BaseTypeHandler<String> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
      throws SQLException {
    if (parameter == null || parameter.isEmpty()) {
      ps.setObject(i, null, Types.INTEGER);
      return;
    }
    try {
      ps.setInt(i, Integer.parseInt(parameter));
    } catch (NumberFormatException e) {
      // 非纯数字时按原始字符串写入，由数据库自行转换
      ps.setString(i, parameter);
    }
  }

  @Override
  public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
    Object value = rs.getObject(columnName);
    return toString(value);
  }

  @Override
  public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    Object value = rs.getObject(columnIndex);
    return toString(value);
  }

  @Override
  public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    Object value = cs.getObject(columnIndex);
    return toString(value);
  }

  private static String toString(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      // 去除小数位（如 1.0 -> 1）
      if (number.doubleValue() == number.longValue()) {
        return String.valueOf(number.longValue());
      }
      return number.toString();
    }
    return value.toString();
  }
}
