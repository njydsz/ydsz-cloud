package com.njydsz.generator.config;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.resource.loader.FileResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Velocity 模板引擎配置。
 *
 * <p>从 classpath:/templates/{templateGroup} 加载 .vm 模板文件。
 * 全局宏定义（velocity_implicit.vm）自动加载，提供 {@code $text} 和 {@code $date} 辅助工具。
 *
 * <p><b>模板分组路径解析逻辑：</b>
 * <pre>
 *   1. 若 ydzsz.generator.template-dir 为文件系统目录（非 classpath: 前缀），则使用 FileResourceLoader
 *   2. 否则使用 ClasspathResourceLoader，加载路径为 classpath:/templates/{templateGroup}
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
public class VelocityConfig {

  /** 默认模板分组名。 */
  private static final String DEFAULT_TEMPLATE_GROUP = "default";

  /** 模板分组（Spring 配置注入，若未配置则使用默认分组）。 */
  @Value("${ydsz.generator.template-group:" + DEFAULT_TEMPLATE_GROUP + "}")
  private String templateGroup;

  /** 外部模板目录（可选，覆盖 classpath 模式）。 */
  @Value("${ydsz.generator.template-dir:classpath}")
  private String templateDir;

  /**
   * 创建并配置 VelocityEngine Bean。
   *
   * @return 配置好的 VelocityEngine 实例
   */
  @Bean
  public VelocityEngine velocityEngine() {
    VelocityEngine engine = createBaseEngine();
    configureResourceLoader(engine);
    engine.init();
    return engine;
  }

  private VelocityEngine createBaseEngine() {
    VelocityEngine engine = new VelocityEngine();
    engine.setProperty(RuntimeConstants.INPUT_ENCODING, "UTF-8");
    engine.setProperty("output.encoding", "UTF-8");
    // 开启 Velocity 宏内联模式，全局宏库可被任意模板引用
    engine.setProperty("velocimacro.permissions.allow.inline", true);
    engine.setProperty("velocimacro.permissions.allow.inline.replace.global", true);
    engine.setProperty("velocimacro.permissions.allow.inline.to.replac.global", true);
    // 配置全局宏库位置
    engine.setProperty(RuntimeConstants.VM_LIBRARY, "velocity_implicit.vm");
    engine.setProperty(RuntimeConstants.VM_LIBRARY_AUTORELOAD, true);
    return engine;
  }

  private void configureResourceLoader(final VelocityEngine engine) {
    if (templateDir != null && !templateDir.isBlank() && !templateDir.startsWith("classpath")) {
      // 外部文件系统目录模式
      engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "file,classpath");
      engine.setProperty("file.resource.loader.class", FileResourceLoader.class.getName());
      engine.setProperty("file.resource.loader.path", templateDir + "/" + resolveGroup());
      engine.setProperty("file.resource.loader.cache", false);
      engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
      engine.setProperty("classpath.resource.loader.path", "/templates/" + resolveGroup());
    } else {
      // classpath 模式
      engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
      engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
      engine.setProperty("classpath.resource.loader.path", "/templates/" + resolveGroup());
    }
  }

  private String resolveGroup() {
    return (templateGroup != null && !templateGroup.isBlank()) ? templateGroup : DEFAULT_TEMPLATE_GROUP;
  }
}
