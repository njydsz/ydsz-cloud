package com.remisoft.system.web;

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
 * 系统基础服务启动类。
 *
 * <p>承载系统配置、数据字典、应用注册（OAuth2 client_id）、系统变量等横切关注点。
 * 复用 common-config（热加载）、common-audit、common-cache 等公共模块。
 *
 * @author remi-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.remisoft.system", "com.remisoft.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszAudit
@EnableYdszSafe
@EnableYdszFeign(basePackages = {"com.remisoft.system.api", "com.remisoft.common.feign", "com.remisoft.userinfo.api", "com.remisoft.project.api"})
@MapperScan("com.remisoft.system.infra.mapper")
@EnableScheduling
public class SystemApplication {

    /**
     * 系统基础服务启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
