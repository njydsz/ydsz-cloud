package com.njydsz.generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.njydsz.generator.config.GeneratorProperties;

/**
 * 代码生成器启动入口
 *
 * <p>独立运行的 Spring Boot 应用，通过 {@code ydsz.generator.*} 配置项
 * 指定目标模块、包名、表名和输出目录，一键生成 DDD 分层 CRUD 代码。
 *
 * <p><b>使用方式：</b>
 *
 * <ol>
 *   <li>在{@code application.yml}中配置{@code ydsz.generator.*}</li>
 *   <li>启动应用触发{@code ApplicationRunner}自动执行生成</li>
 *   <li>通过{@code POST /api/v1/generate}接口触发增量生成</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SpringBootApplication
@EnableConfigurationProperties(GeneratorProperties.class)
public class GeneratorApplication {

  public static void main(String[] args) {
    SpringApplication.run(GeneratorApplication.class, args);
  }
}
