package com.njydsz.userinfo.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户信息中心模块配置
 *
 * <p>统一管理 ydsz-userinfo 微服务的横切关注点：密码编码器、异步任务、缓存、配置属性注册。
 * 该 Configuration 由 ydsz-userinfo-server 的 Spring Boot 启动类通过 @Import 或
 * 组件扫描自动加载。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>注册 {@link PasswordEncoder} Bean（BCrypt，强度可配置）</li>
 *   <li>启用 {@code @EnableAsync}：支撑异步事件、审计日志异步持久化</li>
 *   <li>启用 {@code @EnableCaching}：支撑部门树、角色权限等热点数据缓存</li>
 *   <li>注册 {@link UserInfoProperties} 与 {@link LdapProperties} 配置属性</li>
 * </ul>
 *
 * <p><b>Bean 清单：</b>
 * <table border="1">
 *   <caption>注册的 Bean</caption>
 *   <tr><th>Bean 名称</th><th>类型</th><th>作用域</th><th>说明</th></tr>
 *   <tr><td>passwordEncoder</td><td>{@link BCryptPasswordEncoder}</td><td>Singleton</td>
 *       <td>BCrypt 密码编码器，强度从 {@link UserInfoProperties#getBcryptStrength()} 注入</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see UserInfoProperties 用户中心配置属性
 * @see LdapProperties LDAP 配置属性
 * @see com.njydsz.userinfo.UserInfoApplication ydsz-userinfo 启动类
 */
@Configuration
@EnableAsync
@EnableCaching
@EnableConfigurationProperties({UserInfoProperties.class, LdapProperties.class})
public class UserInfoConfiguration {

    /**
     * 密码编码器 Bean（BCrypt）
     *
     * <p>BCrypt 是一种基于 Blowfish 加密算法的自适应哈希函数，
     * 内置随机盐（每用户独立）+ 可配置 cost（计算轮数）。
     * <p>强度（cost）值越大，单次加密越慢、暴力破解越难；推荐生产 ≥ 10。
     * <p>Spring Security 在认证流程中自动调用该 Bean 完成密码匹配校验。
     *
     * @param properties 用户中心配置属性，用于注入 BCrypt 强度
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder(UserInfoProperties properties) {
        return new BCryptPasswordEncoder(properties.getBcryptStrength());
    }
}
