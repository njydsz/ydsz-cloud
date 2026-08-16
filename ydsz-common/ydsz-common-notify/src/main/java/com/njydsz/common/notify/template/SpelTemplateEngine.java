package com.njydsz.common.notify.template;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * 基于 Spring Expression Language (SpEL) 的模板引擎
 *
 * <p>使用 SpEL 语法（如 #{name}, #{order.amount}）渲染模板， 支持属性访问、方法调用、三元运算等高级特性。
 *
 * <p><b>示例：</b>
 *
 * <pre>{@code
 * render("Hello #{name}, your balance is #{balance > 100 ? 'VIP' : 'Normal'}",
 *        Map.of("name", "Alice", "balance", 200))
 * // -> "Hello Alice, your balance is VIP"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SpelTemplateEngine implements TemplateEngine {

  private static final Logger log = LoggerFactory.getLogger(SpelTemplateEngine.class);

  /** SpEL 表达式匹配模式，匹配 #{...} 格式的表达式 */
  private static final Pattern SPEL_PATTERN = Pattern.compile("#\\{([^}]+)}");

  /** SpEL 表达式解析器（线程安全，可复用） */
  private final ExpressionParser parser = new SpelExpressionParser();

  /** 模板缓存 */
  private final Map<String, NotifyTemplate> templates = new ConcurrentHashMap<>();

  /** 已解析表达式缓存（key=expressionStr, value=Expression），避免每次渲染重复解析 */
  private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

  /** 模板变量校验器（可选依赖，P0-9） */
  private TemplateVariableValidator variableValidator;

  /**
   * 设置模板变量校验器
   *
   * @param validator 变量校验器
   */
  public void setVariableValidator(TemplateVariableValidator validator) {
    this.variableValidator = validator;
  }

  /**
   * 渲染模板，根据 templateId 查找模板并使用变量渲染
   *
   * @param templateId 模板 ID
   * @param variables 模板变量
   * @return 渲染后的内容
   * @throws IllegalArgumentException 如果模板不存在
   */
  @Override
  public String render(String templateId, Map<String, Object> variables) {
    NotifyTemplate template = templates.get(templateId);
    if (template == null) {
      throw new IllegalArgumentException("模板不存在: " + templateId);
    }
    // P0-9: 模板变量校验
    if (variableValidator != null) {
      variableValidator.validate(template, variables);
    }
    return renderTemplate(template.getContent(), variables);
  }

  /**
   * 直接渲染模板字符串（便捷方法）
   *
   * @param template 模板字符串
   * @param variables 模板变量
   * @return 渲染后的内容
   */
  public String renderTemplate(String template, Map<String, Object> variables) {
    if (template == null || template.isEmpty()) {
      return "";
    }
    if (variables == null || variables.isEmpty()) {
      return template;
    }

    try {
      StandardEvaluationContext context = new StandardEvaluationContext();
      variables.forEach(context::setVariable);

      StringBuffer sb = new StringBuffer();
      Matcher matcher = SPEL_PATTERN.matcher(template);
      while (matcher.find()) {
        String expressionStr = matcher.group(1);
        try {
          Expression expression = getOrParseExpression(expressionStr);
          if (expression == null) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            continue;
          }
          Object value = expression.getValue(context);
          String replacement = value != null ? String.valueOf(value) : "";
          matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        } catch (ExpressionException e) {
          log.debug("[SpelTemplateEngine] 表达式求值失败: {}, error={}", expressionStr, e.getMessage());
          matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
        }
      }
      matcher.appendTail(sb);
      return sb.toString();
    } catch (Exception e) {
      log.warn("[SpelTemplateEngine] 渲染失败: template={}, error={}", template, e.getMessage());
      return template;
    }
  }

  /**
   * 获取或解析 SpEL表达式（带缓存）。
   *
   * <p>已解析的表达式会被缓存，避免对同一表达式重复解析。 解析失败时返回 null（调用方应保留原始占位符）。
   *
   * @param expressionStr 去掉 "#{...}" 包裹的表达式字符串
   * @return 解析后的 Expression 实例，解析失败时返回 null
   */
  private Expression getOrParseExpression(String expressionStr) {
    Expression cached = expressionCache.get(expressionStr);
    if (cached != null) {
      return cached;
    }
    try {
      Expression parsed = parser.parseExpression("#" + expressionStr);
      expressionCache.putIfAbsent(expressionStr, parsed);
      return parsed;
    } catch (ExpressionException e) {
      log.debug("[SpelTemplateEngine] 表达式语法错误，无法缓存: {}", expressionStr);
      return null;
    }
  }

  /**
   * 注册模板
   *
   * @param template 模板定义
   */
  @Override
  public void register(NotifyTemplate template) {
    if (template == null || template.getTemplateId() == null) {
      throw new IllegalArgumentException("模板及 templateId 不能为空");
    }
    templates.put(template.getTemplateId(), template);
  }

  /**
   * 判断是否包含指定模板
   *
   * @param templateId 模板 ID
   * @return 是否存在
   */
  @Override
  public boolean hasTemplate(String templateId) {
    return templates.containsKey(templateId);
  }

  /**
   * 获取模板定义
   *
   * @param templateId 模板 ID
   * @return 模板定义，不存在时返回 null
   */
  @Override
  public NotifyTemplate getTemplate(String templateId) {
    return templates.get(templateId);
  }

  /**
   * 移除模板
   *
   * @param templateId 模板 ID
   */
  @Override
  public void unregister(String templateId) {
    templates.remove(templateId);
    log.info("[SpelTemplateEngine] 模板已移除: {}", templateId);
  }

  /**
   * 获取所有已注册的模板
   *
   * @return 模板 Map
   */
  @Override
  public Map<String, NotifyTemplate> getAllTemplates() {
    return Collections.unmodifiableMap(templates);
  }
}
