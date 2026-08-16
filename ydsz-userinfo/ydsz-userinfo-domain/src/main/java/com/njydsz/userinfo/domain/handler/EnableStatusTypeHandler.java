package com.njydsz.userinfo.domain.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import com.njydsz.userinfo.domain.enums.EnableStatusEnum;

/**
 * 启用状态类型处理器。
 *
 * <p>实现数据库整数列（{@code 0=DISABLED, 1=ENABLED}）与 Java {@link EnableStatusEnum} 枚举之间的双向转换。
 *
 * <p>转换规则：
 *
 * <ul>
 *   <li>DB → Java：{@code 0} → {@link EnableStatusEnum#DISABLED}，{@code 1} → {@link EnableStatusEnum#ENABLED}，{@code null} → {@code null}
 *   <li>Java → DB：{@link EnableStatusEnum#DISABLED} → {@code 0}，{@link EnableStatusEnum#ENABLED} → {@code 1}，{@code null} → {@code null}
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@MappedTypes(EnableStatusEnum.class)
@MappedJdbcTypes(JdbcType.INTEGER)
public class EnableStatusTypeHandler extends BaseTypeHandler<EnableStatusEnum> {

  private static final int DISABLED_VALUE = 0;
  private static final int ENABLED_VALUE = 1;

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, EnableStatusEnum parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setInt(i, parameter == EnableStatusEnum.ENABLED ? ENABLED_VALUE : DISABLED_VALUE);
  }

  @Override
  public EnableStatusEnum getNullableResult(ResultSet rs, String columnName) throws SQLException {
    int value = rs.getInt(columnName);
    if (rs.wasNull()) {
      return null;
    }
    return toEnum(value);
  }

  @Override
  public EnableStatusEnum getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    int value = rs.getInt(columnIndex);
    if (rs.wasNull()) {
      return null;
    }
    return toEnum(value);
  }

  @Override
  public EnableStatusEnum getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    int value = cs.getInt(columnIndex);
    if (cs.wasNull()) {
      return null;
    }
    return toEnum(value);
  }

  private EnableStatusEnum toEnum(int value) {
    switch (value) {
      case ENABLED_VALUE:
        return EnableStatusEnum.ENABLED;
      case DISABLED_VALUE:
        return EnableStatusEnum.DISABLED;
      default:
        return EnableStatusEnum.DISABLED;
    }
  }
}
