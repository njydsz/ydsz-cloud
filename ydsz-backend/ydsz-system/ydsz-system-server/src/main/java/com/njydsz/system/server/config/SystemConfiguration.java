package com.njydsz.system.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 系统模块配置。
 *
 * <p>注册 {@link SystemProperties} 和 {@link BCryptPasswordEncoder} Bean。
 * BCrypt 加密强度可通过 {@code ydsz.system.app.bcrypt-strength} 配置。
 *
 * @author ydsz-team
 */
@Configuration
@EnableConfigurationProperties(SystemProperties.class)
public class SystemConfiguration {

    /**
     * BCrypt 密码编码器，强度由配置决定。
     *
     * @param properties 系统配置
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder(SystemProperties properties) {
        int strength = properties.getApp().getBcryptStrength();
        if (strength < 4 || strength > 31) {
            strength = 10;
        }
        return new BCryptPasswordEncoder(strength);
    }
}
