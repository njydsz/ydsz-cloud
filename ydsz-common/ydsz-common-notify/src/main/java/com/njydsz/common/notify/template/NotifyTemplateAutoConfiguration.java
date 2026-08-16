package com.njydsz.common.notify.template;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import lombok.RequiredArgsConstructor;

/**
 * 通知模板引擎自动配置
 *
 * <p>当 ydsz.notify.template.enabled=true（默认）时生效，自动创建模板引擎和模板管理器 Bean。
 *
 * <p>支持的配置项：
 *
 * <ul>
 *   <li>ydsz.notify.template.enabled - 是否启用模板引擎（默认 true）
 *   <li>ydsz.notify.template.base-path - 模板文件基础路径（默认 classpath:notify-templates/）
 *   <li>ydsz.notify.template.cache-enabled - 是否启用模板缓存（默认 true）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(NotifyTemplateProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.notify.template",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NotifyTemplateAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(NotifyTemplateAutoConfiguration.class);

  /** 创建 SpEL 模板引擎 */
  @Bean
  @ConditionalOnMissingBean(TemplateEngine.class)
  public TemplateEngine templateEngine(
      NotifyTemplateProperties properties,
      ObjectProvider<TemplateVariableValidator> validatorProvider) {
    SpelTemplateEngine engine = new SpelTemplateEngine();
    TemplateVariableValidator validator = validatorProvider.getIfAvailable();
    if (validator != null) {
      engine.setVariableValidator(validator);
      log.info("[NotifyTemplateAutoConfiguration] TemplateVariableValidator 已注入到 SpEL 模板引擎");
    }
    log.info("[NotifyTemplateAutoConfiguration] 初始化 SpEL 模板引擎");
    return engine;
  }

  /** 注册预定义的模板 */
  @Bean
  public ApplicationRunner templateRegistrar(
      TemplateEngine templateEngine,
      ObjectProvider<List<NotifyTemplate>> predefinedTemplatesProvider) {

    return args -> {
      List<NotifyTemplate> predefinedTemplates = predefinedTemplatesProvider.getIfAvailable();
      if (predefinedTemplates != null && !predefinedTemplates.isEmpty()) {
        templateEngine.registerAll(predefinedTemplates);
        log.info(
            "[NotifyTemplateAutoConfiguration] 预定义模板已注册: count={}", predefinedTemplates.size());
      }
    };
  }
}
