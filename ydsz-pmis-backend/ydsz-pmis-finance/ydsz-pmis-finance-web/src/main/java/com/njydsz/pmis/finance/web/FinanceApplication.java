package com.njydsz.pmis.finance.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import com.njydsz.pmis.common.auth.annotation.EnableYdszAuth;
import com.njydsz.pmis.common.feign.annotation.EnableYdszFeign;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 财务会计服务启动类
 *
 * <p>承载发票管理、回款管理、费用报销、收入确认、利润核算、对账等财务会计业务能力。
 *
 * <p>DDD 分层架构：
 * <ul>
 *   <li>domain — 实体/DTO/枚举/VO/Converter</li>
 *   <li>infra  — Mapper 接口 + MyBatis XML</li>
 *   <li>server — Service + Engine + Job + Exception</li>
 *   <li>api    — Feign Client 契约 + Fallback</li>
 *   <li>web    — Controller + Config + 启动类</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.finance", "com.njydsz.pmis.common", "com.njydsz.pmis.literule"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszFeign(basePackages = {"com.njydsz.pmis.finance.api", "com.njydsz.pmis.common.feign"})
@MapperScan({"com.njydsz.pmis.finance.infra.mapper", "com.njydsz.pmis.literule.infra.mapper"})
@EnableScheduling
public class FinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
