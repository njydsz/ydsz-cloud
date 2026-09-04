package com.njydsz.generator.cli;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import com.njydsz.generator.config.DatabaseDialect;
import com.njydsz.generator.config.GeneratorProperties;
import com.njydsz.generator.config.VelocityConfig;
import com.njydsz.generator.service.CodeGeneratorService;
import com.njydsz.generator.service.DbTypeConverter;
import com.njydsz.generator.service.TableMetadataReader;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
      System.out.println("========== 代码生成器启动 ==========");
      System.out.println("模块: " + arguments.getModule());
      System.out.println("数据库方言: " + DbTypeConverter.getCurrentDialect()
          .getIdentifier());
      List<String> files = generatorService.generateAllConfigured();
      System.out.println("========== 生成完成: " + files.size() + " 个文件 ==========");
      for (String file : files) {
        System.out.println("  ✓ " + file);
      }
    } catch (Exception e) {
      System.err.println("代码生成失败: " + e.getMessage());
      e.printStackTrace();
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
    System.out.println("""
        代码生成器 CLI — 基于数据库表结构自动生成 DDD 分层 CRUD 代码

        用法:
          java -cp "target/classes:target/dependency/*" \\
               com.njydsz.generator.cli.GeneratorCliApplication [选项]

        选项:
          --module <name>           目标模块名 (如 system, userinfo)
          --package <pkg>           目标包名 (如 com.njydsz.system)
          --tables <list>           表名列表，逗号分隔 (如 ydsz_sys_tenant,ydsz_sys_user)
          --output <dir>            输出目录的绝对路径
          --jdbc-url <url>          JDBC URL (默认: jdbc:postgresql://localhost:5432/ydsz_cloud)
          --jdbc-user <user>        数据库用户名 (默认: ydsz)
          --jdbc-pass <pass>        数据库密码 (默认: ydsz123)
          --jdbc-driver <class>     JDBC 驱动类名 (自动识别，可手动覆盖)
          --table-prefix <prefix>   表名前缀 (默认: ydsz_)
          --author <name>           作者署名 (默认: ydsz-team)
          --conflict <strategy>     文件冲突策略: skip/override/merge/prompt (默认: prompt)
          --config <file>           从 properties 文件加载配置
          --help                    显示此帮助信息

        示例:
          # 为 system 模块的 tenant 表生成代码
          java -cp "..." com.njydsz.generator.cli.GeneratorCliApplication \\
              --module=system \\
              --package=com.njydsz.system \\
              --tables=ydsz_sys_tenant \\
              --output=/path/to/ydsz-cloud

          # 从配置文件运行
          java -cp "..." com.njydsz.generator.cli.GeneratorCliApplication \\
              --config=generator.properties
        """);
  }

  /**
   * CLI 参数封装。
   *
   * @author ydsz-team
   * @since 26.09.04
   */
  private static final class CliArguments {
    private String module;
    private String packageName;
    private List<String> tables;
    private String output;
    private String jdbcUrl = "jdbc:postgresql://localhost:5432/ydsz_cloud";
    private String jdbcUsername = "ydsz";
    private String jdbcPassword = "ydsz123";
    private String jdbcDriver;
    private String tablePrefix = "ydsz_";
    private String author = "ydsz-team";
    private String fileConflictStrategy = "prompt";
    private boolean help;

    static CliArguments parse(String[] args) {
      CliArguments arguments = new CliArguments();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        switch (arg) {
          case "--module" -> arguments.module = args[++i];
          case "--package" -> arguments.packageName = args[++i];
          case "--tables" -> arguments.tables = List.of(args[++i].split(","));
          case "--output" -> arguments.output = args[++i];
          case "--jdbc-url" -> arguments.jdbcUrl = args[++i];
          case "--jdbc-user" -> arguments.jdbcUsername = args[++i];
          case "--jdbc-pass" -> arguments.jdbcPassword = args[++i];
          case "--jdbc-driver" -> arguments.jdbcDriver = args[++i];
          case "--table-prefix" -> arguments.tablePrefix = args[++i];
          case "--author" -> arguments.author = args[++i];
          case "--conflict" -> arguments.fileConflictStrategy = args[++i];
          case "--help", "-h" -> arguments.help = true;
          case "--config" -> loadFromPropertiesFile(args[++i], arguments);
          default -> System.out.println("未知参数: " + arg);
        }
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
