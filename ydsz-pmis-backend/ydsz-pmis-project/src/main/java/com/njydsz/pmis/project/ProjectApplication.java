package com.njydsz.pmis.project;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 项目管理服务启动类
 *
 * <p>承载商机、立项、合同、补充协议、合同变更等业务能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.project", "com.njydsz.pmis.common", "com.njydsz.pmis.literule"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.njydsz.pmis.project.feign")
@MapperScan({"com.njydsz.pmis.project.mapper", "com.njydsz.pmis.literule.mapper"})
@EnableScheduling
public class ProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
