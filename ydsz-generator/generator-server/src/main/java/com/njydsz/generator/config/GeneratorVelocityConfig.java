package com.njydsz.generator.config;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Velocity 模板引擎配置。
 *
 * <p>配置全局宏库（velocity_implicit.vm）自动加载，提供模板内可用的宏：
 * textFirstLower / textFirstUpper / textCamelCase / textRemovePrefix 等。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Configuration
public class GeneratorVelocityConfig {

  /** 全局宏库文件名。 */
  private static final String VM_LIBRARY = "velocity_implicit.vm";

  /**
   * 创建 VelocityEngine Bean。
   *
   * @return 配置好的 VelocityEngine
   */
  @Bean
  public VelocityEngine velocityEngine() {
    VelocityEngine engine = new VelocityEngine();
    engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
    engine.setProperty("classpath.resource.loader.class",
        ClasspathResourceLoader.class.getName());
    engine.setProperty(RuntimeConstants.INPUT_ENCODING, "UTF-8");
    engine.setProperty("output.encoding", "UTF-8");
    // 宏内联模式（全局宏库可被任意模板直接使用，无需显式 #parse）
    engine.setProperty("velocimacro.permissions.allow.inline", true);
    engine.setProperty("velocimacro.permissions.allow.inline.replace.global", true);
    engine.setProperty("velocimacro.permissions.allow.inline.to.replac.global", true);
    // 全局宏库
    engine.setProperty(RuntimeConstants.VM_LIBRARY, VM_LIBRARY);
    engine.setProperty(RuntimeConstants.VM_LIBRARY_AUTORELOAD, true);
    engine.init();
    return engine;
  }
}
