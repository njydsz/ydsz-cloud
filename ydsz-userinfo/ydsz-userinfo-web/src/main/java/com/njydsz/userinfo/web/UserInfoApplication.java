package com.njydsz.userinfo.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.audit.annotation.EnableYdszAudit;
import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;
import com.njydsz.common.safe.annotation.EnableYdszSafe;

/**
 * 用户信息中心服务启动类。
 *
 * <p>承载登录认证、RBAC 权限、组织架构、OAuth2 授权码流程等核心能力。 复用 common-auth（JWT/RBAC/TOTP）、common-audit、common-cache
 * 等公共模块。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.userinfo", "com.njydsz.common"})
@EnableDiscoveryClient
@EnableYdszAudit
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszFeign(basePackages = {"com.njydsz.userinfo.api", "com.njydsz.common.feign"})
@MapperScan("com.njydsz.userinfo.infra.mapper")
@EnableScheduling
public class UserInfoApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserInfoApplication.class, args);
  }
}
