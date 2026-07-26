package com.njydsz.userinfo.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户信息中心模块配置。
 *
 * <p>注册 PasswordEncoder Bean（强度可配置），启用异步和缓存，
 * 注册 LDAP 配置属性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableAsync
@EnableCaching
@EnableConfigurationProperties({UserInfoProperties.class, LdapProperties.class})
public class UserInfoConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder(UserInfoProperties properties) {
        return new BCryptPasswordEncoder(properties.getBcryptStrength());
    }
}
