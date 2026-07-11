package com.njydsz.pmis.project.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 项目管理服务启动类
 *
 * <p>承载项目执行域业务能力：立项/WBS/EVM/风险/工时/采购/预算/报表/驾驶舱。
 * <p>跨域财务数据通过 {@link com.njydsz.pmis.common.feign.FinanceDataClient} Feign 调用获取。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.project", "com.njydsz.pmis.common", "com.njydsz.pmis.literule"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.njydsz.pmis.project.api", "com.njydsz.pmis.common.feign"})
@MapperScan({"com.njydsz.pmis.project.infra.mapper", "com.njydsz.pmis.literule.infra.mapper"})
@EnableScheduling
public class ProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
