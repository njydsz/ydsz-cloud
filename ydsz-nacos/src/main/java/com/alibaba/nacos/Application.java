package com.alibaba.nacos;

import com.alibaba.nacos.config.ConfigConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Nacos 控制台启动类。
 *
 * <p>以独立模式运行 Nacos 控制台，便于本地开发调试。生产环境请从官网下载 ZIP 最新版集群配置运行。
 *
 * @author nacos
 * @since 1.0.0
 */
@Slf4j
@EnableScheduling
@SpringBootApplication
public class Application {

  /**
   * 应用入口方法。
   *
   * <p>初始化运行环境后以独立模式启动 Nacos 控制台。
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    if (initEnv()) {
      SpringApplication.run(Application.class, args);
      log.info("Remi Nacos Startup Completed!");
    }
  }

  /**
   * 初始化运行环境。
   *
   * <p>配置 Nacos 独立模式、身份验证、日志目录等系统属性。
   *
   * @return 初始化成功返回 {@code true}
   */
  private static boolean initEnv() {
    // Nacos 以独立模式运行
    System.setProperty(ConfigConstants.STANDALONE_MODE, "true");
    // 启用 Nacos 的身份验证功能
    System.setProperty(ConfigConstants.AUTH_ENABLED, "true");
    // 设置日志文件存储的基本目录为 logs
    System.setProperty(ConfigConstants.LOG_BASEDIR, "logs");
    // Nacos 不记录日志信息
    System.setProperty(ConfigConstants.LOG_ENABLED, "false");
    // 设置 Nacos 控制台的上下文路径为 /nacos
    System.setProperty(ConfigConstants.NACOS_CONTEXT_PATH, "/nacos");
    return true;
  }
}
