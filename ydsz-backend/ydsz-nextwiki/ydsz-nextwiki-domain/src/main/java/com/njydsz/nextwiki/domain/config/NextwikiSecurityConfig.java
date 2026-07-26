package com.njydsz.nextwiki.domain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * NextWiki 安全配置
 * <p>
 * 注册 BCryptPasswordEncoder 为 Spring Bean，供 ShareDomainService 注入使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
public class NextwikiSecurityConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
