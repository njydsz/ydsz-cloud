package com.njydsz.generator.config;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Velocity 模板引擎配置。
 *
 * <p>从 classpath:/templates 加载 .vm 模板文件。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
public class VelocityConfig {

  /**
   * 创建并配置 VelocityEngine Bean。
   *
   * @return 配置好的 VelocityEngine 实例
   */
  @Bean
  public VelocityEngine velocityEngine() {
    VelocityEngine engine = new VelocityEngine();
    engine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
    engine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
    engine.setProperty(RuntimeConstants.INPUT_ENCODING, "UTF-8");
    engine.setProperty("output.encoding", "UTF-8");
    engine.init();
    return engine;
  }
}
