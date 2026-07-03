package com.njydsz.pmis.userinfo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 身份认证管理服务启动类（合并 user + auth）
 *
 * <p>合并后 auth 不再通过 Feign 调用 user 加载登录上下文，改为本地 Service 直接调用，
 * 降低登录链路延迟与故障点。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {
        "com.njydsz.pmis.userinfo",
        "com.njydsz.pmis.common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.njydsz.pmis.userinfo.feign")
@EnableAsync
@MapperScan("com.njydsz.pmis.userinfo.mapper")
public class UserInfoApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserInfoApplication.class, args);
    }
}
