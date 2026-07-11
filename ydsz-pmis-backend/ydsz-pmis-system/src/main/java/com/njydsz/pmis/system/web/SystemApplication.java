package com.njydsz.pmis.system.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 系统基础服务启动类（合并 file + config + audit + notification + message）
 *
 * <p>合并后 notification 不再通过 Feign 调用 message，改为本地 Service 直接调用，
 * 降低通知投递链路延迟与故障点。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {
        "com.njydsz.pmis.system",
        "com.njydsz.pmis.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.njydsz.pmis.system.api")
@MapperScan("com.njydsz.pmis.system.infra.mapper")
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
