package com.njydsz.common.jdbc.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Boolean 与 SMALLINT（0/1）列的类型转换处理器。
 *
 * <p>全平台统一使用 SMALLINT 存储布尔值（YDIZ-DB-007 P0），Java 实体字段仍使用 {@link Boolean} 类型。
 * 该 TypeHandler 负责将 Java Boolean true/false 映射为 JDBC setInt(1/0)，读取时将 SMALLINT 值转回 Boolean。
 *
 * <p>全局注册后，所有 Boolean 字段（{@code @TableLogic}、普通查询条件、INSERT/UPDATE）均自动走该转换逻辑，
 * 无需在每个实体字段上单独标注 {@code typeHandler = ...}。
 *
 * @author ydsz-team
 * @since 26.10.05
 */
public class BooleanSmallintTypeHandler extends BaseTypeHandler<Boolean> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setInt(i, Boolean.TRUE.equals(parameter) ? 1 : 0);
  }

  @Override
  public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toBoolean(rs.getObject(columnName));
  }

  @Override
  public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toBoolean(rs.getObject(columnIndex));
  }

  @Override
  public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toBoolean(cs.getObject(columnIndex));
  }

  private static Boolean toBoolean(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      // smallint 0=false, 非0=true
      return number.intValue() != 0;
    }
    // PostgreSQL BOOLEAN 残留列向后兼容（理论上不会再有）
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.parseBoolean(value.toString());
  }
}
