package com.njydsz.userinfo.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Userinfo module configuration.
 *
 * <p>Registers PasswordEncoder bean for AuthService password verification.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
public class UserInfoConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
