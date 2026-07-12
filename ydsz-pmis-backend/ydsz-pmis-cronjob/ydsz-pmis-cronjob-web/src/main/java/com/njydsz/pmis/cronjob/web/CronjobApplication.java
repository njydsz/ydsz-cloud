package com.njydsz.pmis.cronjob.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import com.njydsz.pmis.common.auth.annotation.EnableYdszAuth;
import com.njydsz.pmis.common.feign.annotation.EnableYdszFeign;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务调度服务启动类
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.cronjob", "com.njydsz.pmis.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszFeign(basePackages = {"com.njydsz.pmis.cronjob.api", "com.njydsz.pmis.common.feign"})
@EnableScheduling
@MapperScan("com.njydsz.pmis.cronjob.infra.mapper")
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
