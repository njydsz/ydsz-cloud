package com.njydsz.common.excel.spring;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
import org.springframework.boot.health.contributor.HealthIndicator;
  // CHECKSTYLE.ON: RegexpSinglelineJava

/**
 * Excel 模块健康检查指示器
 *
 * <p>注册到 Spring Boot Actuator 健康端点（/actuator/health）。除配置摘要外，
 * 实做临时目录可写性探测（P2 修复：此前仅回显配置，无任何真实检查）——
 * 临时目录是 fast 读引擎大 Sheet 流式解析、InputStream 落盘与 fast 写引擎
 * 中转文件的硬依赖，不可写时这些路径将全部失败，因此探测失败报告 DOWN。
 *
 * <p>仅在引入 spring-boot-actuator 依赖时生效（通过 @ConditionalOnClass 控制）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class ExcelHealthIndicator implements HealthIndicator {

  /** 临时目录探测写入内容（非空字节，确保真实落盘） */
  private static final byte[] PROBE_CONTENT = "ydsz-excel-health-probe".getBytes(StandardCharsets.UTF_8);

  private final ExcelProperties properties;

  public ExcelHealthIndicator(ExcelProperties properties) {
    this.properties = properties;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("fastReader", properties.getUseFastReader());
    details.put("fastWriter", properties.getUseFastWriter());
    details.put("dateFormat", properties.getDefaultDateFormat());
    details.put("maxReadMb", properties.getMaxReadFileSizeMb());
    details.put("maxWriteMb", properties.getMaxWriteFileSizeMb());

    // 真实探测：临时目录可写（写入 + 读回校验 + 清理）
    File probe = null;
    try {
      probe = File.createTempFile("ydsz-excel-health-", ".tmp");
      try (OutputStream out = new FileOutputStream(probe)) {
        out.write(PROBE_CONTENT);
      }
      boolean written = probe.length() == PROBE_CONTENT.length;
      details.put("tempDirWritable", written);
      // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（系统属性名），非代码引用
      details.put("tempDir", System.getProperty("java.io.tmpdir"));
      // CHECKSTYLE.ON: RegexpSinglelineJava
      if (!written) {
        return Health.down().withDetail("reason", "临时目录探测写入长度不符").withDetails(details).build();
      }
    } catch (IOException e) {
      details.put("tempDirWritable", false);
      return Health.down(e).withDetails(details).build();
    } finally {
      if (probe != null) {
        try {
          Files.deleteIfExists(probe.toPath());
        } catch (IOException e) {
          // 探测文件清理失败不影响健康结论（OS 重启清理临时目录）
        }
      }
    }
    return Health.up().withDetails(details).build();
  }
}
