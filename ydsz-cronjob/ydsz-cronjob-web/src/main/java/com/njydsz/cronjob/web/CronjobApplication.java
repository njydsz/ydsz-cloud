package com.njydsz.cronjob.web;

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
 * 定时任务调度服务启动类。
 *
 * <p>基于 Spring Boot 的分布式定时任务调度引擎，支持 Cron 表达式、ISO 8601 周期和固定频率三种调度模式。
 * 核心能力包括：分布式锁保障单次执行、多节点 Leader 选举分区调度、任务执行日志与失败告警、
 * 分片广播策略（平均分配 / 一致性哈希）。
 *
 * <p>扫描范围覆盖 {@code com.njydsz.cronjob}、{@code com.njydsz.common} 和 {@code com.njydsz.message.api}，
 * 启用安全审计、鉴权、定时调度与 MyBatis Mapper 自动装配。
 *
 * @author ydsz
 * @since 26.09.24
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.cronjob", "com.njydsz.common", "com.njydsz.message.api"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszAudit
// P0-FIX: @EnableYdszFeign 为无属性注解（默认扫描 com.njydsz 下 FeignClient），移除不存在的 basePackages 属性
@EnableYdszFeign
@EnableScheduling
@MapperScan("com.njydsz.cronjob.infra.mapper")
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
