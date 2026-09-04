package com.njydsz.generator;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import com.njydsz.generator.service.CodeGeneratorService;

/**
 * 应用启动后自动执行代码生成。
 *
 * <p>通过 {@link ApplicationRunner} 在 Spring 上下文就绪后触发全量代码生成。
 * 若 {@code ydsz.generator.*} 配置完整（moduleName/packageName/tableNames/outputDir），
 * 自动生成 DDD 分层代码并输出到目标目录。
 *
 * <p>生成成功后应用继续运行（作为可选 HTTP 服务提供增量生成接口），
 * 生成失败则打印详细日志但允许应用继续启动（不阻塞微服务整体启动）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class GeneratorRunner implements ApplicationRunner {

  private final CodeGeneratorService generatorService;

  @Override
  public void run(ApplicationArguments args) {
    try {
      log.info("========== 代码生成器启动 ==========");
      List<String> files = generatorService.generateAllConfigured();
      log.info("========== 代码生成完成: {} 个文件 ==========", files.size());
      for (String file : files) {
        log.info("  已生成: {}", file);
      }
    } catch (Exception e) {
      log.error("代码生成失败: {}", e.getMessage(), e);
      log.warn("应用将继续启动（生成失败不阻塞应用运行），请检查配置和数据库连接");
    }
  }
}
