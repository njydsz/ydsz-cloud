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

    /**
     * 注册 BCrypt 密码编码器 Bean。
     * <p>用于对分享链接的访问密码进行安全散列存储与校验，供 {@code ShareDomainService} 注入使用。
     *
     * @return BCryptPasswordEncoder 实例（线程安全，可全局复用）
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
