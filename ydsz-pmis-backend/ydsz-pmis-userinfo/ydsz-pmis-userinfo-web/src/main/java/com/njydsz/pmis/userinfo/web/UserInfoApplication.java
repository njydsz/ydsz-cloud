package com.njydsz.pmis.userinfo.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import com.njydsz.pmis.common.auth.annotation.EnableYdszAuth;

/**
 * 用户信息中心服务启动类（合并 user + auth）
 *
 * <p>合并后 auth 不再通过 Feign 调用 user 加载登录上下文，改为本地 Service 直接调用，
 * 降低登录链路延迟与故障点。
 *
 * <p>P1-9: 移除 @EnableFeignClients(basePpackages = "com.njydsz.pmis.userinfo.api")，
 * 因 UserAuthClient（唯一的自调用 FeignClient）已删除，userinfo 模块不再持有任何 FeignClient。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {
        "com.njydsz.pmis.userinfo",
        "com.njydsz.pmis.common"
})
@EnableDiscoveryClient
@EnableYdszAuth
@MapperScan("com.njydsz.pmis.userinfo.infra.mapper")
public class UserInfoApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserInfoApplication.class, args);
    }
}
