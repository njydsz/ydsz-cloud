package com.njydsz.nextwiki.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.audit.annotation.EnableYdszAudit;
import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;
import com.njydsz.common.locales.config.EnableYdszI18n;
import com.njydsz.common.safe.annotation.EnableYdszSafe;

/**
 * 网盘知识库服务启动类
 *
 * <p>融合文件存储（common-file）、文档解析（common-docs）、全文搜索、在线预览、 版本控制、分享协作的一体化网盘知识库平台。
 *
 * <p>@EnableAsync 已移至 {@code AsyncConfig} 统一管理异步线程池。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ComponentScan(basePackages = {"com.njydsz.nextwiki", "com.njydsz.common"}, excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = org.springframework.boot.autoconfigure.AutoConfiguration.class))
@SpringBootApplication
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszAudit
@EnableYdszFeign
@EnableYdszI18n
@MapperScan("com.njydsz.nextwiki.infra.mapper")
@EnableScheduling
public class NextwikiApplication {

  public static void main(String[] args) {
    SpringApplication.run(NextwikiApplication.class, args);
  }
}
