package com.njydsz.generator.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.njydsz.generator.service.CodeGenService;

/**
 * 代码生成器 CLI 应用入口。
 *
 * <p>独立运行的命令行 Spring Boot 应用，不启动 Web 服务。
 * 通过 {@link CommandLineRunner} 在启动后立即执行代码生成，
 * 完成后退出，适用于 CI/CD 集成或命令行工具场景。
 *
 * <p>使用方式：
 * <pre>
 *   java -jar ydsz-generator-app.jar --ds=1 --group=1 --table=t_user --out=./out
 * </pre>
 *
 * <p><b>DDD 分层位置：</b>app 模块，独立于 web/server 的 CLI 形态。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "com.njydsz.generator")
public class GeneratorCliApplication implements CommandLineRunner {

  /** 代码生成领域服务（来自 server 层）。 */
  @Autowired
  private CodeGenService codeGenService;

  /**
   * 应用入口。
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(GeneratorCliApplication.class);
    // CLI 模式，不启动 Web 服务
    app.setWebApplicationType(WebApplicationType.NONE);
    app.run(args);
  }

  /** {@inheritDoc} */
  @Override
  public void run(String... args) {
    log.info("========================================");
    log.info("  ydzs-generator CLI 代码生成器");
    log.info("========================================");

    // TODO: 解析命令行参数，调用 CodeGenService 生成
    log.info("提示：请通过 ydsz-generator-web 的 REST API 使用代码生成功能");
    log.info("或扩展此 CLI 以解析参数调用 CodeGenService.generate()");
  }
}
