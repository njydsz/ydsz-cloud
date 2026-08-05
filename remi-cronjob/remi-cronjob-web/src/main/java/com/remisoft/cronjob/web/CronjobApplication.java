package com.remisoft.cronjob.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.remisoft.common.audit.annotation.EnableRemiAudit;
import com.remisoft.common.auth.annotation.EnableRemiAuth;
import com.remisoft.common.feign.annotation.EnableRemiFeign;
import com.remisoft.common.safe.annotation.EnableRemiSafe;

/**
 * 定时任务调度服务启动类
 *
 * @author remi-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.remisoft.cronjob", "com.remisoft.common"})
@EnableDiscoveryClient
@EnableRemiAuth
@EnableRemiSafe
@EnableRemiAudit
@EnableRemiFeign(basePackages = {"com.remisoft.cronjob.api", "com.remisoft.common.feign", "com.remisoft.userinfo.api", "com.remisoft.system.api"})
@EnableScheduling
@MapperScan("com.remisoft.cronjob.infra.mapper")
public class CronjobApplication {

    /**
     * 应用入口方法
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CronjobApplication.class, args);
    }
}
