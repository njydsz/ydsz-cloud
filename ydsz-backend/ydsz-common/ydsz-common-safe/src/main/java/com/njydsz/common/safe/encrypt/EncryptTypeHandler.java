package com.njydsz.common.safe.encrypt;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * 字段加密 TypeHandler
 *
 * <p>MyBatis TypeHandler，在写入数据库时自动加密，查询时自动解密。
 * 配合 {@link EncryptField} 注解使用。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @TableName(autoResultMap = true)
 * public class User {
 *     @TableField(typeHandler = EncryptTypeHandler.class)
 *     @EncryptField
 *     private String idCard;
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    private static FieldEncryptionService encryptionService;

    /**
     * 设置加密服务实例（由自动配置注入）
     *
     * @param service 加密服务
     */
    public static void setEncryptionService(FieldEncryptionService service) {
        encryptionService = service;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        if (encryptionService == null) {
            throw new IllegalStateException("FieldEncryptionService 未初始化，请检查配置");
        }
        ps.setString(i, encryptionService.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return decryptIfNeeded(value);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return decryptIfNeeded(value);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return decryptIfNeeded(value);
    }

    private String decryptIfNeeded(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (encryptionService == null) {
            throw new IllegalStateException("FieldEncryptionService 未初始化，请检查配置");
        }
        try {
            return encryptionService.decrypt(value);
        } catch (Exception e) {
            // 解密失败可能是历史明文数据，返回原值
            return value;
        }
    }
}
