package com.njydsz.generator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 代码生成器 Web 启动类。
 *
 * <p>扫描 com.njydsz.generator 包路径下的全部 Component / Service / Repository / Mapper。
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
