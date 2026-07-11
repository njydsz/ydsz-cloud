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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ExecutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutionApplication.class, args);
    }
}
