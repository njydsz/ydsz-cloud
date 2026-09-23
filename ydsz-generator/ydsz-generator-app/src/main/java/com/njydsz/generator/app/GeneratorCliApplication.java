package com.njydsz.generator.app;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.njydsz.generator.enums.ConflictStrategyEnum;
import com.njydsz.generator.query.GenCodeGenerateQuery;
import com.njydsz.generator.service.CodeGenService;
import com.njydsz.generator.vo.GenResultVO;

/**
 * 代码生成器 CLI 应用入口。
 *
 * <p>独立运行的命令行 Spring Boot 应用，不启动 Web 服务。通过 {@link CommandLineRunner}
 * 在启动后立即执行代码生成，完成后退出，适用于 CI/CD 集成或命令行工具场景。
 *
 * <p><b>DDD 分层位置：</b>app 模块，独立于 web/server 的 CLI 形态。
 *
 * <p>使用方式：
 * <pre>
 *   # 单表生成
 *   java -jar ydsz-generator-app.jar --ds=1 --group=1 --table=t_user --out=./out
 *
 *   # 全表批量生成
 *   java -jar ydsz-generator-app.jar --ds=1 --group=1 --all --out=./out
 *
 *   # 指定冲突策略
 *   java -jar ydsz-generator-app.jar --ds=1 --group=1 --table=t_user --out=./out --strategy=APPEND
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "com.njydsz.generator")
public class GeneratorCliApplication implements CommandLineRunner {

  /** 默认冲突策略。 */
  private static final ConflictStrategyEnum DEFAULT_STRATEGY = ConflictStrategyEnum.SKIP;
  /** 最小初始容量。 */
  private static final int MIN_INITIAL_CAPACITY = 8;
  /** 命令行参数前缀。 */
  private static final String ARG_PREFIX = "--";

  private final CodeGenService codeGenService;

  /**
   * 构造器注入 CodeGenService。
   *
   * @param codeGenService 代码生成领域服务
   */
  public GeneratorCliApplication(CodeGenService codeGenService) {
    this.codeGenService = codeGenService;
  }

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
    log.info("  ydzs-generator CLI 代码生成器 v26.09.08");
    log.info("========================================");

    Map<String, String> params = parseArgs(args);
    Long datasourceId = parseLong(params.get("ds"));
    Long templateGroupId = parseLong(params.get("group"));
    String tableName = params.get("table");
    String outputDir = params.get("out");
    String strategy = params.get("strategy");
    boolean isAll = params.containsKey("all");

    if (datasourceId == null || templateGroupId == null || outputDir == null
        || (!isAll && tableName == null)) {
      printUsage();
      return;
    }

    try {
      ConflictStrategyEnum strategyEnum = resolveStrategy(strategy);
      GenResultVO result;
      if (isAll) {
        result = codeGenService.generateAll(
            datasourceId, templateGroupId, outputDir, strategyEnum, "cli");
      } else {
        GenCodeGenerateQuery query = GenCodeGenerateQuery.builder()
            .datasourceId(datasourceId)
            .templateGroupId(templateGroupId)
            .tableName(tableName)
            .outputDir(outputDir)
            .conflictStrategy(strategyEnum.name())
            .triggeredBy("cli")
            .build();
        result = codeGenService.generate(query);
      }
      log.info("生成完成: success={} skip={} fail={}",
          result.getSuccessCount(), result.getSkipCount(), result.getFailCount());
    } catch (Exception e) {
      log.error("CLI 生成失败: {}", e.getMessage(), e);
    }
  }

  /**
   * 解析命令行参数（格式：--key=value）。
   *
   * @param args 原始参数数组
   * @return 参数键值映射（不含 -- 前缀）
   */
  private Map<String, String> parseArgs(String[] args) {
    Map<String, String> params = new HashMap<>(MIN_INITIAL_CAPACITY);
    for (String arg : args) {
      if (!arg.startsWith(ARG_PREFIX)) {
        // 无 -- 前缀表示布尔标记
        params.put(arg, "true");
        continue;
      }
      String kv = arg.substring(ARG_PREFIX.length());
      int eqIdx = kv.indexOf('=');
      if (eqIdx > 0) {
        params.put(kv.substring(0, eqIdx), kv.substring(eqIdx + 1));
      } else {
        params.put(kv, "true");
      }
    }
    return params;
  }

  /**
   * 安全解析 Long 类型参数。
   *
   * @param value 字符串值（可为 null）
   * @return Long 值，解析失败返回 null
   */
  private Long parseLong(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 解析冲突策略字符串。
   *
   * @param strategy 策略名称（可为 null）
   * @return 冲突策略枚举，未识别时返回默认值
   */
  private ConflictStrategyEnum resolveStrategy(String strategy) {
    if (strategy == null || strategy.isEmpty()) {
      return DEFAULT_STRATEGY;
    }
    try {
      return ConflictStrategyEnum.valueOf(strategy.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("未知冲突策略 strategy={}，使用默认 SKIP", strategy);
      return DEFAULT_STRATEGY;
    }
  }

  /**
   * 打印 CLI 使用帮助。
   */
  private void printUsage() {
    log.info("用法:");
    log.info("  单表生成 : --ds=<数据源ID> --group=<模板分组ID> --table=<表名> --out=<输出目录> [--strategy=SKIP|OVERRIDE|APPEND]");
    log.info("  全表生成 : --ds=<数据源ID> --group=<模板分组ID> --all --out=<输出目录> [--strategy=SKIP|OVERRIDE|APPEND]");
    log.info("");
    log.info("参数说明:");
    log.info("  --ds       数据源 ID（必填）");
    log.info("  --group    模板分组 ID（必填）");
    log.info("  --table    表名（与 --all 二选一）");
    log.info("  --all      生成数据源下全部表");
    log.info("  --out      输出目录（必填）");
    log.info("  --strategy 冲突策略：SKIP(默认) / OVERRIDE / APPEND");
  }
}
