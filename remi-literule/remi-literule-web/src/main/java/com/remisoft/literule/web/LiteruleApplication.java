package com.remisoft.literule.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.remisoft.common.audit.annotation.EnableYdszAudit;
import com.remisoft.common.auth.annotation.EnableYdszAuth;
import com.remisoft.common.feign.annotation.EnableYdszFeign;
import com.remisoft.common.safe.annotation.EnableYdszSafe;

/**
 * 规则引擎服务启动类
 *
 * <p>承载 REMI 规则引擎核心能力：规则定义/编排/评估/灰度/回放/审批/CEP。
 * <p>独立部署、独立 JVM 进程，注册到 Nacos，对外提供 REST API（{@code /ruleEngine/**}）。
 *
 * <h3>端口与构建顺序</h3>
 * <ul>
 *   <li>端口：9007（按构建顺序 8/10）</li>
 *   <li>服务名：{@code remi-literule}</li>
 *   <li>配置中心：Nacos（共享 {@code remi-common.yaml} + 本服务 data-id）</li>
 * </ul>
 *
 * <h3>跨服务联动</h3>
 * <ul>
 *   <li>规则触发 → 通过 {@code CronjobServiceClient} 联动定时任务</li>
 *   <li>规则触发 → 通过 {@code WorkflowServiceClient} 联动工作流审批</li>
 *   <li>规则触发 → 通过 {@code DefaultAlertActionHandler} 联动消息中心告警</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.remisoft.literule", "com.remisoft.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszAudit
@EnableYdszFeign(basePackages = {
        "com.remisoft.literule.api",
        "com.remisoft.common.feign",
        "com.remisoft.cronjob.api",
        "com.remisoft.workflow.api",
        "com.remisoft.system.api"
})
@EnableScheduling
@MapperScan("com.remisoft.literule.infra.mapper")
public class LiteruleApplication {

    /**
     * 应用入口方法
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LiteruleApplication.class, args);
    }
}
