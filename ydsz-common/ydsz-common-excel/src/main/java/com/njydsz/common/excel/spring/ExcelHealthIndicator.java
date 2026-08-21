package com.njydsz.common.excel.spring;

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
 * <p>注册到 Spring Boot Actuator 健康端点（/actuator/health）， 仅做模块加载状态检查——不涉及外部依赖，因此始终返回 UP。 提供配置摘要作为诊断明细。
 *
 * <p>仅在引入 spring-boot-actuator 依赖时生效（通过 @ConditionalOnClass 控制）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class ExcelHealthIndicator implements HealthIndicator {

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
    return Health.up().withDetails(details).build();
  }
}
