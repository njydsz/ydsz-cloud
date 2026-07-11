package com.njydsz.pmis.sales;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 售前售后域服务启动类
 *
 * <p>承载商机、合同、立项、结项、售后等业务能力。
 *
 * <p>P1-8: scanBasePackages 显式包含 {@code com.njydsz.pmis.common}，确保 common 模块的
 * Aspect（IdempotentAspect / OperationLogAspect / RateLimiterAspect）、AuditFieldFiller、
 * GlobalExceptionHandler 等公共 Bean 被正确注册到本服务容器，避免未来加业务代码时注解静默失效。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.sales", "com.njydsz.pmis.common"})
@MapperScan("com.njydsz.pmis.sales.mapper")
@EnableDiscoveryClient
@EnableFeignClients
public class SalesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesApplication.class, args);
    }
}
