package com.njydsz.generator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 代码生成器 Web 启动类。
 *
 * <p>扫描 com.njydsz.generator 包路径下的全部 Component / Service / Repository / Mapper。
 *
 * <p><b>刻意不扫描 com.njydsz.common（架构决策，勿随意改动）：</b>
 *
 * <ul>
 *   <li>生成器为开发期工具，安全边界自管：通过 {@code GeneratorSecurityConfig} 自建
 *       SecurityFilterChain，不接入 common-auth 的 TokenService 认证链
 *   <li>common-web 的 GlobalResponseAdvice / WebMvcConfiguration 等扫描态 Bean 不适用，
 *       其能力由 AutoConfiguration.imports 自动装配按需生效
 *   <li>如需接入平台统一认证，须先评审 GeneratorSecurityConfig 的替代方案
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@SpringBootApplication(scanBasePackages = "com.njydsz.generator")
@MapperScan("com.njydsz.generator.mapper")
public class GeneratorWebApplication {

  /**
   * 应用入口。
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    SpringApplication.run(GeneratorWebApplication.class, args);
  }
}
