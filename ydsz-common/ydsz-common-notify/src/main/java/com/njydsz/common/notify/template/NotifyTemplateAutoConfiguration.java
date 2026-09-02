package com.njydsz.common.notify.template;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
 * @since 26.09.01
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

  private static final Logger LOG = LoggerFactory.getLogger(NotifyTemplateAutoConfiguration.class);

  /**
   * 创建 SpEL 模板引擎。
   *
   * <p>变量校验器为可选依赖：装配后可在渲染前拦截缺失变量，未装配时缺参问题要到渲染阶段才暴露为渲染失败。
   *
   * @param properties 模板配置属性，决定模板基础路径与是否启用模板缓存
   * @param validatorProvider 模板变量校验器提供者，可选依赖；未装配时跳过渲染前校验
   * @return 模板引擎实例，不会为 {@code null}
   */
  @Bean
  @ConditionalOnMissingBean(TemplateEngine.class)
  public TemplateEngine templateEngine(
      NotifyTemplateProperties properties,
      ObjectProvider<TemplateVariableValidator> validatorProvider) {
    SpelTemplateEngine engine = new SpelTemplateEngine();
    TemplateVariableValidator validator = validatorProvider.getIfAvailable();
    if (validator != null) {
      engine.setVariableValidator(validator);
      LOG.info("[NotifyTemplateAutoConfiguration] TemplateVariableValidator 已注入到 SpEL 模板引擎");
    }
    LOG.info("[NotifyTemplateAutoConfiguration] 初始化 SpEL 模板引擎");
    return engine;
  }

  /**
   * 注册预定义的模板。
   *
   * <p>以 {@link ApplicationRunner} 形式在容器就绪后执行，确保业务侧声明的 {@link NotifyTemplate} Bean
   * 已全部装配完成再统一注册，避免注册时遗漏尚未初始化的模板；未定义任何模板时仅跳过，不视为启动失败。
   *
   * @param templateEngine 模板引擎，预定义模板将批量注册到其中
   * @param predefinedTemplatesProvider 业务侧声明的预定义模板列表提供者，可选依赖； 未装配或为空列表时不执行注册
   * @return 容器就绪后执行模板注册的启动任务，不会为 {@code null}
   */
  @Bean
  public ApplicationRunner templateRegistrar(
      TemplateEngine templateEngine,
      ObjectProvider<List<NotifyTemplate>> predefinedTemplatesProvider) {

    return args -> {
      List<NotifyTemplate> predefinedTemplates = predefinedTemplatesProvider.getIfAvailable();
      if (predefinedTemplates != null && !predefinedTemplates.isEmpty()) {
        templateEngine.registerAll(predefinedTemplates);
        LOG.info(
            "[NotifyTemplateAutoConfiguration] 预定义模板已注册: count={}", predefinedTemplates.size());
      }
    };
  }
}
