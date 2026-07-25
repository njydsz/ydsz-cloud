package com.njydsz.system.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;

/**
 * 系统基础服务启动类。
 *
 * <p>承载系统配置、数据字典、应用注册（OAuth2 client_id）、系统变量等横切关注点。
 * 复用 common-config（热加载）、common-audit、common-cache 等公共模块。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.system", "com.njydsz.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszFeign(basePackages = {"com.njydsz.system.api", "com.njydsz.common.feign"})
@MapperScan("com.njydsz.system.infra.mapper")
@EnableScheduling
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
