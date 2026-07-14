package com.njydsz.pmis.nextwiki.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.pmis.common.auth.annotation.EnableYdszAuth;
import com.njydsz.pmis.common.feign.annotation.EnableYdszFeign;

/**
 * 网盘知识库服务启动类
 *
 * <p>融合文件存储（common-file）、文档解析（common-docs）、全文搜索、在线预览、
 * 版本控制、分享协作的一体化网盘知识库平台。
 *
 * <p>@EnableAsync 已移至 {@code AsyncConfig} 统一管理异步线程池。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.nextwiki", "com.njydsz.pmis.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszFeign(basePackages = {"com.njydsz.pmis.nextwiki.api", "com.njydsz.pmis.common.feign"})
@MapperScan("com.njydsz.pmis.nextwiki.infra.mapper")
@EnableScheduling
public class NextwikiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NextwikiApplication.class, args);
    }
}
