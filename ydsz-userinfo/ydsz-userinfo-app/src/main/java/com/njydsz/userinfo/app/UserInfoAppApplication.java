package com.njydsz.userinfo.app;

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
 * 用户信息中心服务 App 端启动类（P1-2 双入口架构）。
 *
 * <p>当 {@code ydsz.userinfo.platform=app} 时以此入口启动，提供移动端/应用端 API。
 * 与 {@code ydsz-userinfo-web} 互斥，共享 server/domain 层，通过
 * {@link com.njydsz.userinfo.app.config.ConditionalOnPlatform} 控制组件激活。
 *
 * <p><b>启动方式：</b>
 * <pre>{@code
 * java -jar ydsz-userinfo-app.jar --ydsz.userinfo.platform=app
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.userinfo", "com.njydsz.common"})
@EnableDiscoveryClient
@EnableYdszAudit
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszFeign
@MapperScan("com.njydsz.userinfo.infra.mapper")
@EnableScheduling
public class UserInfoAppApplication {

  public static void main(String[] args) {
    SpringApplication.run(UserInfoAppApplication.class, args);
  }
}
