package com.njydsz.message.infra.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import com.njydsz.message.domain.enums.receipt.ReadStatusEnum;

/**
 * MyBatis 类型处理器：数据库 int(0/1) ↔ Java {@link ReadStatusEnum} 双向转换。
 *
 * <p>数据库列 {@code read_status} 存储 0（未读）/ 1（已读），实体字段使用类型安全的枚举。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ReadStatusTypeHandler extends BaseTypeHandler<ReadStatusEnum> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, ReadStatusEnum parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setInt(i, "READ".equals(parameter.name()) ? 1 : 0);
  }

  @Override
  public ReadStatusEnum getNullableResult(ResultSet rs, String columnName) throws SQLException {
    int value = rs.getInt(columnName);
    return rs.wasNull() ? ReadStatusEnum.UNREAD : (value == 1 ? ReadStatusEnum.READ : ReadStatusEnum.UNREAD);
  }

  @Override
  public ReadStatusEnum getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    int value = rs.getInt(columnIndex);
    return rs.wasNull() ? ReadStatusEnum.UNREAD : (value == 1 ? ReadStatusEnum.READ : ReadStatusEnum.UNREAD);
  }

  @Override
  public ReadStatusEnum getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    int value = cs.getInt(columnIndex);
    return cs.wasNull() ? ReadStatusEnum.UNREAD : (value == 1 ? ReadStatusEnum.READ : ReadStatusEnum.UNREAD);
  }
}
