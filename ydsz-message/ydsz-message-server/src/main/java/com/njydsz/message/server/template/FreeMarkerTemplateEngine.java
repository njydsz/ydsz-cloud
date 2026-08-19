package com.njydsz.message.server.template;

import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * FreeMarker 模板引擎实现。
 *
 * <p>作为默认模板引擎，提供高性能的模板渲染能力。 支持 FreeMarker 标准语法：变量替换 {@code ${var}}、条件 {@code <#if>}、循环 {@code <#list>} 等。
 *
 * <p>对于仍使用旧语法（{@code {{#if}}} / {@code {{#each}}}）的模板， 回退到 {@link DefaultTemplateEngine} 渲染。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Primary
@Component
public class FreeMarkerTemplateEngine implements TemplateEngine {

  private final Configuration freemarkerConfiguration;
  private final DefaultTemplateEngine fallbackEngine;

  public FreeMarkerTemplateEngine(Configuration freemarkerConfiguration, DefaultTemplateEngine fallbackEngine) {
    this.freemarkerConfiguration = freemarkerConfiguration;
    this.fallbackEngine = fallbackEngine;
  }

  @Override
  public String render(String template, Map<String, Object> params) {
    return render(template, params, null);
  }

  @Override
  public String render(String template, Map<String, Object> params, Set<String> requiredKeys) {
    if (template == null || template.isEmpty()) {
      return "";
    }
    // 保留原行为：params 为 null 且无必填校验时，返回原模板
    if (params == null && (requiredKeys == null || requiredKeys.isEmpty())) {
      return template;
    }
    // 必填参数校验
    if (requiredKeys != null && !requiredKeys.isEmpty()) {
      validateRequired(params, requiredKeys);
    }
    // 检测是否使用旧语法，如果是则回退到自研引擎
    if (isLegacyTemplate(template)) {
      log.debug("[Template] 检测到旧语法模板，回退到自研引擎渲染");
      return fallbackEngine.render(template, params, requiredKeys);
    }
    try {
      Template freemarkerTemplate = new Template("inline", template, freemarkerConfiguration);
      StringWriter writer = new StringWriter();
      freemarkerTemplate.process(params != null ? params : Map.of(), writer);
      return writer.toString();
    } catch (TemplateException e) {
      log.warn("[Template] FreeMarker 渲染失败，回退到自研引擎: err={}", e.getMessage());
      return fallbackEngine.render(template, params, requiredKeys);
    } catch (Exception e) {
      log.error("[Template] FreeMarker 渲染异常: err={}", e.getMessage(), e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .message("模板渲染失败: " + e.getMessage())
          .build();
    }
  }

  /**
   * 校验必填参数。
   *
   * @param params 参数映射
   * @param requiredKeys 必填 key 集合
   */
  private void validateRequired(Map<String, Object> params, Set<String> requiredKeys) {
    Map<String, Object> safeParams = params != null ? params : Map.of();
    for (String key : requiredKeys) {
      Object value = safeParams.get(key);
      if (value == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("模板必填参数缺失: " + key)
            .build();
      }
      if (value instanceof String s && s.isBlank()) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("模板必填参数为空: " + key)
            .build();
      }
    }
  }

  /**
   * 检测模板是否使用旧语法（{{#if}} / {{#each}}）。
   *
   * @param template 模板内容
   * @return true 表示使用旧语法
   */
  private boolean isLegacyTemplate(String template) {
    return template.contains("{{#if") || template.contains("{{#each") || template.contains("${this") || template.contains("${@index");
  }
}
