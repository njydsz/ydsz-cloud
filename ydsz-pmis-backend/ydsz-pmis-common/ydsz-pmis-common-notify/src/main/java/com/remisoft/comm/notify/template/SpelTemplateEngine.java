package com.remisoft.comm.notify.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Spring Expression Language (SpEL) 的模板引擎
 *
 * <p>使用 SpEL 语法（如 #{name}, #{order.amount}）渲染模板，
 * 支持属性访问、方法调用、三元运算等高级特性。
 *
 * <p><b>示例：</b>
 * <pre>{@code
 * render("Hello #{name}, your balance is #{balance > 100 ? 'VIP' : 'Normal'}",
 *        Map.of("name", "Alice", "balance", 200))
 * // -> "Hello Alice, your balance is VIP"
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 1.0.0
 */
public class SpelTemplateEngine implements TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(SpelTemplateEngine.class);

    /** SpEL 表达式匹配模式，匹配 #{...} 格式的表达式 */
    private static final Pattern SPEL_PATTERN = Pattern.compile("#\\{([^}]+)}");

    /** SpEL 表达式解析器 */
    private final ExpressionParser parser = new SpelExpressionParser();

    /** 模板缓存 */
    private final Map<String, NotifyTemplate> templates = new ConcurrentHashMap<>();

    /**
     * 渲染模板，根据 templateId 查找模板并使用变量渲染
     *
     * @param templateId 模板 ID
     * @param variables  模板变量
     * @return 渲染后的内容
     * @throws IllegalArgumentException 如果模板不存在
     */
    @Override
    public String render(String templateId, Map<String, Object> variables) {
        NotifyTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        return renderTemplate(template.getContent(), variables);
    }

    /**
     * 直接渲染模板字符串（便捷方法）
     *
     * @param template  模板字符串
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
                    Expression expression = parser.parseExpression("#" + expressionStr);
                    Object value = expression.getValue(context);
                    String replacement = value != null ? String.valueOf(value) : "";
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                } catch (Exception e) {
                    log.debug("[SpelTemplateEngine] 表达式解析失败: {}, error={}", expressionStr, e.getMessage());
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

    @Override
    public void register(NotifyTemplate template) {
        if (template == null || template.getTemplateId() == null) {
            throw new IllegalArgumentException("模板及 templateId 不能为空");
        }
        templates.put(template.getTemplateId(), template);
    }

    @Override
    public boolean hasTemplate(String templateId) {
        return templates.containsKey(templateId);
    }

    @Override
    public NotifyTemplate getTemplate(String templateId) {
        return templates.get(templateId);
    }

    @Override
    public void unregister(String templateId) {
        templates.remove(templateId);
        log.info("[SpelTemplateEngine] 模板已移除: {}", templateId);
    }

    @Override
    public java.util.Map<String, NotifyTemplate> getAllTemplates() {
        return java.util.Collections.unmodifiableMap(templates);
    }
}
