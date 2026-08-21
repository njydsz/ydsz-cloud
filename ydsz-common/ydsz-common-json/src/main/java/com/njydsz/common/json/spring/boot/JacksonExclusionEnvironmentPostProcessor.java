package com.njydsz.common.json.spring.boot;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

/**
 * 可选排除 Spring Boot Jackson 自动配置（默认共存，A-3 修复）。
 *
 * <p>默认行为：<b>共存优先</b>——不触碰全局 Jackson 自动配置，springdoc-openapi / actuator 等依赖 {@code ObjectMapper}
 * Bean 的组件可正常工作；MVC 层通过 {@code JsonHttpMessageConverter} 的注册顺序已足以让业务接口走 YdszJson。
 *
 * <p>当显式设置 {@code ydsz.json.disable-jackson-auto-configuration=true} 时， 将 {@code
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
 * org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration} 追加到 {@code
  // CHECKSTYLE.ON: RegexpSinglelineJava
 * spring.autoconfigure.exclude}，Spring 容器不再注册 {@code ObjectMapper} Bean。
 *
 * <p>EnvironmentPostProcessor 在 Spring Boot 启动早期执行， 此时 {@code @ConfigurationProperties} 尚未绑定，因此直接从
 * {@link Environment} 读取原始属性值。
 *
 * <p><b>合并逻辑（A-3 修复）：</b>遍历全部 PropertySource，收集各来源中已有的 {@code spring.autoconfigure.exclude}
 * 值后统一合并，再以高优先级 {@link MapPropertySource} 覆盖。原先仅读取 {@code getProperty()} 解析出的单个最高优先级值，会导致
 * base/profile 等多个来源定义的 exclude 列表相互覆盖、条目丢失。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JacksonExclusionEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String PROPERTY_NAME = "ydsz.json.disable-jackson-auto-configuration";
  private static final String EXCLUDE_PROPERTY = "spring.autoconfigure.exclude";
  private static final String JACKSON_AUTO_CONFIGURATION =
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
      "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration";
  // CHECKSTYLE.ON: RegexpSinglelineJava
  private static final String PROPERTY_SOURCE_NAME = "ydszJsonJacksonExclusion";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    // 默认共存：仅当显式开启时才排除 Jackson（A-3 修复：原先默认排除过于激进）
    if (!Boolean.TRUE.equals(
        environment.getProperty(PROPERTY_NAME, Boolean.class, Boolean.FALSE))) {
      return;
    }

    Set<String> excludes = collectExistingExcludes(environment);
    excludes.add(JACKSON_AUTO_CONFIGURATION);

    // 通过高优先级 PropertySource 覆盖 spring.autoconfigure.exclude（已合并全部来源）
    String merged = String.join(",", excludes);
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                PROPERTY_SOURCE_NAME, Collections.singletonMap(EXCLUDE_PROPERTY, merged)));
  }

  /**
   * 收集全部 PropertySource 中已有的 exclude 值，避免单一来源读取导致条目丢失。
   *
   * @param environment 配置环境
   * @return 去重后的排除项集合（有序）
   */
  private Set<String> collectExistingExcludes(ConfigurableEnvironment environment) {
    Set<String> excludes = new LinkedHashSet<>();
    for (PropertySource<?> source : environment.getPropertySources()) {
      if (!source.containsProperty(EXCLUDE_PROPERTY)) {
        continue;
      }
      Object value = source.getProperty(EXCLUDE_PROPERTY);
      if (value == null) {
        continue;
      }
      for (String item : String.valueOf(value).split(",")) {
        if (StringUtils.hasText(item)) {
          excludes.add(item.trim());
        }
      }
    }
    return excludes;
  }
}
