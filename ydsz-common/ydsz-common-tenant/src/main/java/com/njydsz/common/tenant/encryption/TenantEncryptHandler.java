package com.njydsz.common.tenant.encryption;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.metrics.TenantMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * 租户级加密 MyBatis TypeHandler。
 *
 * <p>自动使用当前租户的 AES-256-GCM 密钥对字段值进行加解密。
 * 密钥从 {@link TenantProperties#getTenantEncryptionKeys()} 按租户 ID 查找。
 *
 * <p><b>密文格式：</b>Base64(12字节IV + 密文 + 16字节AuthTag)。
 *
 * <p><b>注册方式：</b>在 mybatis-config.xml 中注册，或
 * 通过 auto-type-handlers-package 自动扫描。
 *
 * <p><b>已知限制：</b>
 * <ul>
 *   <li>加密后无法直接用于 WHERE 条件查询</li>
 *   <li>密钥轮换需要手动迁移数据</li>
 *   <li>不支持加密字段的模糊查询和排序</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see TenantEncrypt
 */
@Slf4j
@MappedTypes(String.class)
public class TenantEncryptHandler extends BaseTypeHandler<String> {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;

    private final Map<String, String> encryptionKeys;
    private final ObjectProvider<TenantMetrics> metricsProvider;

    public TenantEncryptHandler(TenantProperties properties) {
        this(properties, null);
    }

    public TenantEncryptHandler(TenantProperties properties,
                                ObjectProvider<TenantMetrics> metricsProvider) {
        this.encryptionKeys = properties.getTenantEncryptionKeys();
        this.metricsProvider = metricsProvider;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter,
                                    JdbcType jdbcType) throws SQLException {
        String encrypted = encrypt(parameter);
        if (encrypted != null) {
            ps.setString(i, encrypted);
            recordMetric("encrypt");
        } else {
            // 无密钥配置时回退到明文存储（开发环境）
            log.debug("租户未配置加密密钥，字段以明文存储");
            ps.setString(i, parameter);
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return decrypt(value);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return decrypt(value);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return decrypt(value);
    }

    /**
     * 使用当前租户的密钥加密。
     *
     * @param plaintext 明文
     * @return Base64 编码的密文，无密钥返回 null
     */
    private String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        byte[] key = getCurrentTenantKey();
        if (key == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // IV + ciphertext + tag
            byte[] result = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            log.error("租户级加密失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用当前租户的密钥解密。
     *
     * @param ciphertext Base64 编码的密文
     * @return 明文，非密文（无前缀）返回原值
     */
    private String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        byte[] key = getCurrentTenantKey();
        if (key == null) {
            return ciphertext;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            if (decoded.length <= IV_LENGTH + TAG_LENGTH) {
                // 不是有效密文格式，返回原值
                return ciphertext;
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
            System.arraycopy(decoded, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), spec);
            byte[] plaintext = cipher.doFinal(encrypted);
            recordMetric("decrypt");
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 非 Base64 格式 → 可能是明文存储的遗留数据
            return ciphertext;
        } catch (Exception e) {
            log.warn("租户级密文解密失败，返回原值: {}", e.getMessage());
            return ciphertext;
        }
    }

    /**
     * 获取当前租户的加密密钥。
     *
     * @return 32 字节密钥，未配置返回 null
     */
    private byte[] getCurrentTenantKey() {
        TenantContext context = TenantContextHolder.get();
        if (context == null || encryptionKeys == null) {
            return null;
        }
        String keyBase64 = encryptionKeys.get(context.getTenantId());
        if (keyBase64 == null) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException e) {
            log.error("租户 {} 加密密钥格式无效", context.getTenantId());
            return null;
        }
    }

    private void recordMetric(String operation) {
        TenantMetrics metrics = metricsProvider != null ? metricsProvider.getIfAvailable() : null;
        if (metrics != null) {
            // Use incrementCounter via reflection or just rely on interceptPass for now
            // to avoid circular dependency on SQL interception
        }
    }
}
