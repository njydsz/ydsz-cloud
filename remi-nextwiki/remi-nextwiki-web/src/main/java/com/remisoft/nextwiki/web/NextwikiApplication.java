package com.remisoft.nextwiki.web;

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
 * 网盘知识库服务启动类
 *
 * <p>融合文件存储（common-file）、文档解析（common-docs）、全文搜索、在线预览、
 * 版本控制、分享协作的一体化网盘知识库平台。
 *
 * <p>@EnableAsync 已移至 {@code AsyncConfig} 统一管理异步线程池。
 *
 * @author remi-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.remisoft.nextwiki", "com.remisoft.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszAudit
@EnableYdszFeign(basePackages = {"com.remisoft.nextwiki.api", "com.remisoft.common.feign", "com.remisoft.userinfo.api", "com.remisoft.agent.api"})
@MapperScan("com.remisoft.nextwiki.infra.mapper")
@EnableScheduling
public class NextwikiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NextwikiApplication.class, args);
    }
}
