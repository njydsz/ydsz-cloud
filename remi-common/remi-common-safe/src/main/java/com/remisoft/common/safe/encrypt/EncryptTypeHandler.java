package com.remisoft.common.safe.encrypt;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p><b>解密失败处理：</b>读取数据库字段时，若值符合密文格式但解密失败
 * （GCM 认证失败 / 密钥版本缺失 / 密文被篡改），按 {@link DecryptFailureStrategy} 处理：
 * <ul>
 *   <li>{@link DecryptFailureStrategy#THROW}（默认）— 抛异常，阻止读取链路继续；</li>
 *   <li>{@link DecryptFailureStrategy#RETURN_MASKED} — 返回打码值 {@code ****}；</li>
 *   <li>{@link DecryptFailureStrategy#RETURN_ORIGINAL} — 返回原值并记录告警（不推荐）。</li>
 * </ul>
 *
 * <p><b>历史明文兼容：</b>对于不符合密文格式的值（如历史未加密的明文数据），
 * 直接原样返回，不触发解密流程，便于灰度迁移。
 *
 * @author remi-team
 * @author remi-team
 * @since 1.0.0
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptTypeHandler.class);

    /**
     * 密文的最小字节长度：1 字节版本号 + 12 字节 IV + 16 字节 GCM Tag = 29 字节
     *
     * <p>Base64 编码后约为 40 个字符（29 字节 → 4 个 3 字节组向上取整 = 40 字符含 padding）。
     * 此常量用于 {@link #looksLikeCiphertext(String)} 启发式判断，避免对历史明文调用解密逻辑。
     */
    private static final int MIN_CIPHERTEXT_BYTES = 1 + 12 + 16;

    private static volatile FieldEncryptionService encryptionService;
    private static volatile DecryptFailureStrategy failureStrategy = DecryptFailureStrategy.THROW;
    private static volatile String maskedValue = "****";

    /**
     * 设置加密服务实例（由自动配置注入）
     *
     * @param service 加密服务
     */
    public static void setEncryptionService(FieldEncryptionService service) {
        encryptionService = service;
    }

    /**
     * 设置解密失败处理策略（由自动配置注入）
     *
     * @param strategy    失败策略
     * @param maskedValue 打码值（仅 {@link DecryptFailureStrategy#RETURN_MASKED} 时生效）
     */
    public static void setFailureStrategy(DecryptFailureStrategy strategy, String maskedValue) {
        failureStrategy = strategy == null ? DecryptFailureStrategy.THROW : strategy;
        EncryptTypeHandler.maskedValue = (maskedValue == null || maskedValue.isEmpty())
                ? "****" : maskedValue;
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

    /**
     * 解密字段值
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>空值：直接返回；</li>
     *   <li>非密文格式（历史明文）：直接返回，不触发解密；</li>
     *   <li>密文格式但解密失败：按 {@link #failureStrategy} 策略处理。</li>
     * </ol>
     *
     * @param value 数据库字段原始值
     * @return 解密后的明文，或按失败策略返回的降级值
     */
    private String decryptIfNeeded(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (encryptionService == null) {
            throw new IllegalStateException("FieldEncryptionService 未初始化，请检查配置");
        }
        // 历史明文兼容：非密文格式直接返回，不触发解密
        if (!looksLikeCiphertext(value)) {
            return value;
        }
        try {
            return encryptionService.decrypt(value);
        } catch (Exception e) {
            return handleDecryptFailure(value, e);
        }
    }

    /**
     * 启发式判断字符串是否符合密文格式
     *
     * <p>判断条件：
     * <ul>
     *   <li>能被 Base64 解码；</li>
     *   <li>解码后字节数 >= {@value #MIN_CIPHERTEXT_BYTES}（1B 版本 + 12B IV + 16B Tag）；</li>
     *   <li>首字节（版本号）落在 [1, 127] 区间（密钥版本通常为正整数）。</li>
     * </ul>
     *
     * <p>此判断仅用于区分「历史明文」与「密文」，不保证 100% 准确。
     * 对于极少数符合上述格式的明文（如长随机串），可能误判为密文并触发解密失败，
     * 此时可通过 {@link DecryptFailureStrategy#RETURN_ORIGINAL} 策略兜底。
     *
     * @param value 待判断的字符串
     * @return true 表示可能是密文，需要调用解密；false 表示是明文，直接返回
     */
    static boolean looksLikeCiphertext(String value) {
        if (value == null || value.length() < 40) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length < MIN_CIPHERTEXT_BYTES) {
                return false;
            }
            // 首字节是密钥版本号，应为正整数
            int version = decoded[0] & 0xFF;
            return version >= 1 && version <= 127;
        } catch (IllegalArgumentException ex) {
            // 不是合法的 Base64，肯定是明文
            return false;
        }
    }

    /**
     * 处理解密失败：按策略返回对应结果
     *
     * @param value 原始密文值
     * @param cause 解密失败异常
     * @return 按策略返回的值（THROW 策略会抛异常）
     */
    private String handleDecryptFailure(String value, Exception cause) {
        DecryptFailureStrategy strategy = failureStrategy;
        String masked = maskedValue;
        switch (strategy) {
            case RETURN_MASKED:
                log.warn("字段解密失败，返回打码值 | valueLen={}, cause={}",
                        value == null ? 0 : value.length(), cause.getMessage());
                return masked;
            case RETURN_ORIGINAL:
                log.warn("字段解密失败，返回原值（failure-strategy=RETURN_ORIGINAL，建议尽快修复） | valueLen={}, cause={}",
                        value == null ? 0 : value.length(), cause.getMessage());
                return value;
            case THROW:
            default:
                log.error("字段解密失败，按 fail-safe 策略抛出异常 | valueLen={}",
                        value == null ? 0 : value.length(), cause);
                throw new IllegalStateException("字段解密失败：密文可能被篡改或密钥版本缺失", cause);
        }
    }
}
