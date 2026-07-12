package com.njydsz.pmis.sales.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import com.njydsz.pmis.common.auth.annotation.EnableYdszAuth;
import com.njydsz.pmis.common.feign.annotation.EnableYdszFeign;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商务销售服务启动类
 *
 * <p>承载商机管理、合同管理（含变更/补充协议/模板）等商务销售业务能力。
 *
 * <p>DDD 分层架构：
 * <ul>
 *   <li>domain — 实体/DTO/枚举/VO/Converter</li>
 *   <li>infra  — Mapper 接口 + MyBatis XML</li>
 *   <li>server — Service + Engine + Exception</li>
 *   <li>api    — Feign Client 契约 + Fallback</li>
 *   <li>web    — Controller + Config + 启动类</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.sales", "com.njydsz.pmis.common", "com.njydsz.pmis.literule"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszFeign(basePackages = {"com.njydsz.pmis.sales.api", "com.njydsz.pmis.common.feign"})
@MapperScan({"com.njydsz.pmis.sales.infra.mapper", "com.njydsz.pmis.literule.infra.mapper"})
@EnableScheduling
public class SalesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesApplication.class, args);
    }
}
