package com.njydsz.pmis.common.kms;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * KMS 密钥管理配置属性
 *
 * <p>对应配置前缀 {@code pmis.kms}，支持以下配置项：
 * <ul>
 *   <li>{@code pmis.kms.provider}：密钥提供者类型（environment / jasypt / vault）</li>
 *   <li>{@code pmis.kms.secrets.*}：明文或 ENC() 密钥配置 Map</li>
 *   <li>{@code pmis.kms.vault.*}：Vault 配置（未来扩展，当前预留）</li>
 * </ul>
 *
 * <p>示例配置：
 * <pre>{@code
 * pmis:
 *   kms:
 *     provider: environment
 *     secrets:
 *       db.password: ${DB_PASSWORD:}
 *       redis.password: ${REDIS_PASSWORD:}
 *       jwt.secret: ${PMIS_JWT_SECRET:}
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.kms")
public class KmsProperties {

    /**
     * 密钥提供者类型
     *
     * <p>可选值：
     * <ul>
     *   <li>{@code environment}：从环境变量/Nacos 配置读取（默认，开发阶段）</li>
     *   <li>{@code jasypt}：通过 Jasypt 解密 ENC() 密文</li>
     *   <li>{@code vault}：从 HashiCorp Vault 读取（未来扩展，预留）</li>
     * </ul>
     */
    private String provider = "environment";

    /**
     * 密钥配置 Map
     *
     * <p>键为密钥标识（如 {@code db.password}），值为明文或 {@code ENC()} 密文。
     * 开发环境可直接配置明文，生产环境建议使用 {@code ENC()} 加密或环境变量注入。
     */
    private Map<String, String> secrets = new HashMap<>();

    /**
     * Vault 配置（未来扩展，当前仅预留接口不实际引入依赖）
     */
    private VaultConfig vault = new VaultConfig();

    /**
     * HashiCorp Vault 配置（未来扩展预留）
     *
     * <p>当前项目处于开发阶段不引入 Vault，仅保留配置结构，
     * 便于后续接入 spring-cloud-vault 时直接复用。
     *
     * @author ydsz-pmis-team
     * @since 1.0.0
     */
    @Data
    @NoArgsConstructor
    public static class VaultConfig {
        /** Vault 服务地址，如 https://vault.example.com:8200 */
        private String host;
        /** Vault 服务端口，默认 8200 */
        private int port = 8200;
        /** Vault 认证 Token（生产环境通过环境变量注入） */
        private String token;
        /** Vault 挂载路径，如 secret */
        private String backend = "secret";
        /** 是否启用 Vault（默认 false，未来扩展时由业务开启） */
        private boolean enabled = false;
    }
}
