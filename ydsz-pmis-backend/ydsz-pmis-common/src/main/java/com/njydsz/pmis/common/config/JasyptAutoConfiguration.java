package com.njydsz.pmis.common.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jasypt 配置加密自动配置（P0-4：敏感配置加密）
 *
 * <p>通过环境变量 {@code JASYPT_ENCRYPTOR_PASSWORD} 传入加密密钥，
 * 配置文件中使用 {@code ENC(加密后的密文)} 格式引用加密值。
 *
 * <p>使用方式：
 * <ol>
 *   <li>通过 Jasypt CLI 加密敏感值：{@code java -cp jasypt.jar ... encrypt input=明文 password=密钥}</li>
 *   <li>在配置文件中替换：{@code spring.datasource.password=ENC(加密后的密文)}</li>
 *   <li>启动时传入：{@code -DJASYPT_ENCRYPTOR_PASSWORD=密钥} 或环境变量</li>
 * </ol>
 *
 * <p>安全策略：
 * <ul>
 *   <li>密钥通过环境变量传入，不写入配置文件</li>
 *   <li>使用 PBEWithMD5AndDES 算法（兼容性）+ 1000 次迭代</li>
 *   <li>生产环境建议替换为 PBEWithHmacSHA512AndAES_256</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Configuration
public class JasyptAutoConfiguration {

    /**
     * 加密密钥（从环境变量注入，不允许默认值）。
     * 若未设置则 Jasypt 自动配置不会生效，不影响明文配置的使用。
     */
    @Value("${jasypt.encryptor.password:}")
    private String password;

    /**
     * 注册 Jasypt 字符串加密器
     *
     * @return StringEncryptor 实例
     */
    @Bean(name = "jasyptStringEncryptor")
    @ConditionalOnMissingBean(StringEncryptor.class)
    public StringEncryptor stringEncryptor() {
        if (password == null || password.isEmpty()) {
            // 未配置密钥时返回空加密器（不加密），避免启动失败
            return new PooledPBEStringEncryptor();
        }
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm("PBEWithMD5AndDES");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setStringOutputType("base64");
        encryptor.setConfig(config);
        return encryptor;
    }
}