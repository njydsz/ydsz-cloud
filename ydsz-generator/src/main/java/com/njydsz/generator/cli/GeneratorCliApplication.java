package com.njydsz.generator.cli;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.njydsz.generator.config.DatabaseDialect;
import com.njydsz.generator.config.GeneratorProperties;
import com.njydsz.generator.config.VelocityConfig;
import com.njydsz.generator.service.CodeGeneratorService;
import com.njydsz.generator.service.DbTypeConverter;
import com.njydsz.generator.service.TableMetadataReader;

/**
 * 代码生成器 CLI 启动入口（脱离 Spring Boot）。
 *
 * <p>适合 CI/CD 脚本、Makefile 或命令行直接运行，无需启动完整的 Spring Boot 应用。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * java -cp "target/classes:target/dependency/*" \
 *      com.njydsz.generator.cli.GeneratorCliApplication \
 *      --module=system \
 *      --package=com.njydsz.system \
 *      --tables=ydsz_sys_tenant \
 *      --output=./ydsz-cloud
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.04
 */
public final class GeneratorCliApplication {

  private static final Logger LOG = LoggerFactory.getLogger(GeneratorCliApplication.class);

  private GeneratorCliApplication() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * CLI 入口。
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    try {
      CliArguments arguments = CliArguments.parse(args);
      if (arguments.isHelp()) {
        printUsage();
        return;
      }

      // 1. 初始化 Velocity 引擎
      VelocityEngine velocityEngine = new VelocityConfig().velocityEngine();

      // 2. 初始化数据源
      DataSource dataSource = createDataSource(arguments);
      DbTypeConverter.setDialectByUrl(arguments.getJdbcUrl());

      // 3. 初始化配置
      GeneratorProperties properties = createProperties(arguments);
      TableMetadataReader metadataReader = new TableMetadataReader(dataSource, properties);
      CodeGeneratorService generatorService = new CodeGeneratorService(
          properties, metadataReader, velocityEngine
      );

      // 4. 执行生成
      LOG.info("========== 代码生成器启动 ==========");
      LOG.info("模块: {}", arguments.getModule());
      LOG.info("数据库方言: {}", DbTypeConverter.getCurrentDialect().getIdentifier());
      List<String> files = generatorService.generateAllConfigured();
      LOG.info("========== 生成完成: {} 个文件 ==========", files.size());
      for (String file : files) {
        LOG.info("  ✓ {}", file);
      }
    } catch (Exception e) {
      LOG.error("代码生成失败: {}", e.getMessage(), e);
      System.exit(1);
    }
  }

  private static DataSource createDataSource(CliArguments args) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setUrl(args.getJdbcUrl());
    ds.setUsername(args.getJdbcUsername());
    ds.setPassword(args.getJdbcPassword());
    if (args.getJdbcDriver() != null && !args.getJdbcDriver().isBlank()) {
      ds.setDriverClassName(args.getJdbcDriver());
    } else {
      ds.setDriverClassName(DatabaseDialect.fromJdbcUrl(args.getJdbcUrl())
          .getDriverClassName());
    }
    return ds;
  }

  private static GeneratorProperties createProperties(CliArguments args) {
    GeneratorProperties properties = new GeneratorProperties();
    properties.setModuleName(args.getModule());
    properties.setPackageName(args.getPackage());
    properties.setTableNames(args.getTables());
    properties.setOutputDir(args.getOutput());
    properties.setTablePrefix(args.getTablePrefix());
    properties.setAuthor(args.getAuthor());
    properties.setFileConflictStrategy(args.getFileConflictStrategy());
    return properties;
  }

  private static void printUsage() {
    LOG.info("代码生成器 CLI — 基于数据库表结构自动生成 DDD 分层 CRUD 代码");
    LOG.info("");
    LOG.info("用法:");
    LOG.info("  java -cp \"target/classes:target/dependency/*\" \\");
    LOG.info("       com.njydsz.generator.cli.GeneratorCliApplication [选项]");
    LOG.info("");
    LOG.info("选项:");
    LOG.info("  --module <name>           目标模块名 (如 system, userinfo)");
    LOG.info("  --package <pkg>           目标包名 (如 com.njydsz.system)");
    LOG.info("  --tables <list>           表名列表，逗号分隔 (如 ydsz_sys_tenant,ydsz_sys_user)");
    LOG.info("  --output <dir>            输出目录的绝对路径");
    LOG.info(
        "  --jdbc-url <url>          JDBC URL (默认: jdbc:postgresql://localhost:5432/ydsz_cloud)");
    LOG.info("  --jdbc-user <user>        数据库用户名 (默认: ydsz)");
    LOG.info("  --jdbc-pass <pass>        数据库密码 (默认: ydsz123)");
    LOG.info("  --jdbc-driver <class>     JDBC 驱动类名 (自动识别，可手动覆盖)");
    LOG.info("  --table-prefix <prefix>   表名前缀 (默认: ydsz_)");
    LOG.info("  --author <name>           作者署名 (默认: ydsz-team)");
    LOG.info("  --conflict <strategy>     文件冲突策略: skip/override/merge/prompt (默认: prompt)");
    LOG.info("  --config <file>           从 properties 文件加载配置");
    LOG.info("  --help                    显示此帮助信息");
    LOG.info("");
    LOG.info("示例:");
    LOG.info("  # 为 system 模块的 tenant 表生成代码");
    LOG.info("  java -cp \"...\" com.njydsz.generator.cli.GeneratorCliApplication \\");
    LOG.info("      --module=system \\");
    LOG.info("      --package=com.njydsz.system \\");
    LOG.info("      --tables=ydsz_sys_tenant \\");
    LOG.info("      --output=/path/to/ydsz-cloud");
    LOG.info("");
    LOG.info("  # 从配置文件运行");
    LOG.info("  java -cp \"...\" com.njydsz.generator.cli.GeneratorCliApplication \\");
    LOG.info("      --config=generator.properties");
  }

  /**
   * CLI 参数封装。
   *
   * @author ydsz-team
   * @since 26.09.04
   */
  private static final class CliArguments {

    /** 模块名 */
    private String module;
    /** 包名 */
    private String packageName;
    /** 表名列表 */
    private List<String> tables;
    /** 输出目录 */
    private String output;
    /** JDBC URL */
    private String jdbcUrl = "jdbc:postgresql://localhost:5432/ydsz_cloud";
    /** 数据库用户名 */
    private String jdbcUsername = "ydsz";
    /** 数据库密码 */
    private String jdbcPassword = "ydsz123";
    /** JDBC 驱动类名 */
    private String jdbcDriver;
    /** 表名前缀 */
    private String tablePrefix = "ydsz_";
    /** 作者署名 */
    private String author = "ydsz-team";
    /** 文件冲突策略 */
    private String fileConflictStrategy = "prompt";
    /** 是否请求帮助 */
    private boolean help;

    static CliArguments parse(String[] args) {
      CliArguments arguments = new CliArguments();
      int index = 0;
      while (index < args.length) {
        String arg = args[index];
        switch (arg) {
          case "--module" -> {
            index++;
            arguments.module = args[index];
          }
          case "--package" -> {
            index++;
            arguments.packageName = args[index];
          }
          case "--tables" -> {
            index++;
            arguments.tables = List.of(args[index].split(","));
          }
          case "--output" -> {
            index++;
            arguments.output = args[index];
          }
          case "--jdbc-url" -> {
            index++;
            arguments.jdbcUrl = args[index];
          }
          case "--jdbc-user" -> {
            index++;
            arguments.jdbcUsername = args[index];
          }
          case "--jdbc-pass" -> {
            index++;
            arguments.jdbcPassword = args[index];
          }
          case "--jdbc-driver" -> {
            index++;
            arguments.jdbcDriver = args[index];
          }
          case "--table-prefix" -> {
            index++;
            arguments.tablePrefix = args[index];
          }
          case "--author" -> {
            index++;
            arguments.author = args[index];
          }
          case "--conflict" -> {
            index++;
            arguments.fileConflictStrategy = args[index];
          }
          case "--help", "-h" -> arguments.help = true;
          case "--config" -> {
            index++;
            loadFromPropertiesFile(args[index], arguments);
          }
          default -> LOG.info("未知参数: {}", arg);
        }
        index++;
      }
      return arguments;
    }

    private static void loadFromPropertiesFile(String path, CliArguments arguments) {
      try {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(Paths.get(path))) {
          props.load(is);
        }
        if (props.containsKey("module")) {
          arguments.module = props.getProperty("module");
        }
        if (props.containsKey("package")) {
          arguments.packageName = props.getProperty("package");
        }
        if (props.containsKey("tables")) {
          arguments.tables = List.of(props.getProperty("tables").split(","));
        }
        if (props.containsKey("output")) {
          arguments.output = props.getProperty("output");
        }
        if (props.containsKey("jdbc.url")) {
          arguments.jdbcUrl = props.getProperty("jdbc.url");
        }
        if (props.containsKey("jdbc.username")) {
          arguments.jdbcUsername = props.getProperty("jdbc.username");
        }
        if (props.containsKey("jdbc.password")) {
          arguments.jdbcPassword = props.getProperty("jdbc.password");
        }
      } catch (Exception e) {
        throw new RuntimeException("加载配置文件失败: " + path, e);
      }
    }

    String getModule() {
      return module;
    }

    String getPackage() {
      return packageName;
    }

    List<String> getTables() {
      return tables;
    }

    String getOutput() {
      return output;
    }

    String getJdbcUrl() {
      return jdbcUrl;
    }

    String getJdbcUsername() {
      return jdbcUsername;
    }

    String getJdbcPassword() {
      return jdbcPassword;
    }

    String getJdbcDriver() {
      return jdbcDriver;
    }

    String getTablePrefix() {
      return tablePrefix;
    }

    String getAuthor() {
      return author;
    }

    String getFileConflictStrategy() {
      return fileConflictStrategy;
    }

    boolean isHelp() {
      return help;
    }
  }
}
