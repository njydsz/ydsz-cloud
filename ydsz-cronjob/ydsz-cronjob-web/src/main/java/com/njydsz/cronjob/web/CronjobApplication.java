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
 * 定时任务调度服务启动类
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.cronjob", "com.njydsz.common"})
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
