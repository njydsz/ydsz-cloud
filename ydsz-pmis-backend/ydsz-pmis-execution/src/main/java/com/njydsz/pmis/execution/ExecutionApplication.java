package com.njydsz.pmis.execution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 项目执行域服务启动类
 *
 * <p>承载 WBS、工时、风险、采购、EVM、预算、交付等业务能力。
 *
 * <p>P1-8: scanBasePackages 显式包含 {@code com.njydsz.pmis.common}，确保 common 模块的
 * Aspect（IdempotentAspect / OperationLogAspect / RateLimiterAspect）、AuditFieldFiller、
 * GlobalExceptionHandler 等公共 Bean 被正确注册到本服务容器，避免未来加业务代码时注解静默失效。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.execution", "com.njydsz.pmis.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class ExecutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutionApplication.class, args);
    }
}
