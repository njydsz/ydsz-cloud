package com.njydsz.example.feign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import com.njydsz.common.feign.annotation.EnableYdszFeign;

/**
 * Feign Quickstart 启动类。
 *
 * <p>演示如何启用 YdszFeign 并定义 FeignClient 接口。通过访问以下端点验证功能：
 *
 * <ul>
 *   <li>{@code GET http://localhost:8080/demo/user/1} — 自动解包 Result → UserVO
 *   <li>{@code GET http://localhost:8080/actuator/health/feign} — Feign 模块健康状态
 * </ul>
 *
 * <p><b>启动前提：</b>需要 Nacos 注册中心（或 Spring Cloud Config）准备好 user-service 的目标地址。
 */
@SpringBootApplication
@EnableYdszFeign
@EnableFeignClients(basePackages = "com.njydsz.example.feign.client")
public class FeignQuickstartApplication {

  public static void main(String[] args) {
    SpringApplication.run(FeignQuickstartApplication.class, args);
  }
}
