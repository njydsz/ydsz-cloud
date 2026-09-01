package com.njydsz.nextwiki.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * NextWiki 移动端应用启动入口。
 *
 * <p><b>双入口架构：</b>
 *
 * <ul>
 *   <li>{@code ydsz-nextwiki-web} — PC 端 REST API 入口
 *   <li>{@code ydsz-nextwiki-app} — 移动端 API 入口（本类）
 * </ul>
 *
 * <p>两个入口共享同一套 server 层（应用服务），仅 Controller 层根据端特性做差异化适配。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SpringBootApplication(scanBasePackages = "com.njydsz.nextwiki")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.njydsz.nextwiki.api.client")
public class NextwikiAppApplication {

  public static void main(String[] args) {
    SpringApplication.run(NextwikiAppApplication.class, args);
  }
}
