package com.remisoft.userinfo.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.remisoft.common.audit.annotation.EnableYdszAudit;
import com.remisoft.common.auth.annotation.EnableYdszAuth;
import com.remisoft.common.feign.annotation.EnableYdszFeign;
import com.remisoft.common.safe.annotation.EnableYdszSafe;

/**
 * 用户信息中心服务启动类。
 *
 * <p>承载登录认证、RBAC 权限、组织架构、OAuth2 授权码流程等核心能力。
 * 复用 common-auth（JWT/RBAC/TOTP）、common-audit、common-cache 等公共模块。
 *
 * @author remi-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.remisoft.userinfo", "com.remisoft.common"})
@EnableDiscoveryClient
@EnableYdszAudit
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszFeign(basePackages = {"com.remisoft.userinfo.api", "com.remisoft.common.feign"})
@MapperScan("com.remisoft.userinfo.infra.mapper")
@EnableScheduling
public class UserInfoApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserInfoApplication.class, args);
    }
}
