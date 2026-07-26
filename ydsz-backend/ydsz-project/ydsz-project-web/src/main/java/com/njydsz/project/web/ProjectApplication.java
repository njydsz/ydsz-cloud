package com.njydsz.project.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.audit.annotation.EnableYdszAudit;
import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;
import com.njydsz.common.safe.annotation.EnableYdszSafe;

/**
 * 项目核心业务域启动类。
 *
 * <p>承载立项/合同/执行/EVM/成本/利润/资源/质保/工单/满意度等核心业务逻辑。
 * 复用 common-lock、common-audit、common-cache、common-excel、common-notify 等公共模块。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.project", "com.njydsz.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszAudit
@EnableYdszSafe
@EnableYdszFeign(basePackages = {"com.njydsz.project.api", "com.njydsz.common.feign"})
@MapperScan("com.njydsz.project.infra.mapper")
@EnableScheduling
public class ProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
