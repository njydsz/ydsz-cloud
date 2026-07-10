package com.njydsz.pmis.workflow.engine;

import com.njydsz.pmis.workflow.entity.notification.FlowNotifyTemplateDO;
import com.njydsz.pmis.workflow.mapper.notification.FlowNotifyTemplateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P1-2: 通知模板解析器
 *
 * <p>从 {@code pmis_flow_notify_template} 表读取模板，替换 {@code ${var}} 占位符后返回最终标题和内容。
 * 模板不存在时返回 null，调用方回退到硬编码默认值。
 *
 * <p>P1-5: 新增 {@code locale} 多语言支持。{@link #resolve(String, String, String, String, Map)}
 * 按 locale 优先匹配，未命中时降级到默认 locale（zh_CN），再未命中时降级到无 locale 查询（向后兼容）。
 *
 * <p>占位符示例：{@code ${flowName}} / {@code ${nodeName}} / {@code ${assigneeName}} /
 * ${instanceId} / ${taskId} / ${operatorName} / ${reason}} /
 * ${comment} / ${dueAt} / ${reminderCount} / ${maxReminders}}
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Component
public class FlowNotifyTemplateResolver {

    /** ${var} 占位符正则 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");

    /** P1-5: 默认 locale（未配置 locale 时的降级语言） */
    public static final String DEFAULT_LOCALE = "zh_CN";

    private final FlowNotifyTemplateMapper templateMapper;

    public FlowNotifyTemplateResolver(
            ObjectProvider<FlowNotifyTemplateMapper> provider) {
        this.templateMapper = provider.getIfAvailable();
        if (this.templateMapper == null) {
            log.info("[FlowNotifyTemplate] Mapper 不可用，通知将使用硬编码默认值");
        }
    }

    /**
     * 解析模板（向后兼容：不指定 locale，走默认逻辑）。
     *
     * @param tenantId     租户 ID
     * @param templateCode 模板编码（如 TASK_CREATED）
     * @param channel      通知通道（如 INAPP）
     * @param variables    变量上下文
     * @return 解析后的 [title, content]，模板不存在返回 null
     */
    public String[] resolve(String tenantId, String templateCode, String channel,
                             Map<String, Object> variables) {
        return resolve(tenantId, templateCode, channel, null, variables);
    }

    /**
     * P1-5: 解析模板（指定 locale，支持多语言）。
     *
     * <p>匹配优先级：
     * <ol>
     *   <li>精确匹配 locale（如 "en_US"）</li>
     *   <li>降级到默认 locale（"zh_CN"）</li>
     *   <li>降级到无 locale 查询（向后兼容旧数据，locale 列允许 NULL）</li>
     * </ol>
     *
     * @param tenantId     租户 ID
     * @param templateCode 模板编码
     * @param channel      通知通道
     * @param locale       语言区域（null/空时使用默认 zh_CN）
     * @param variables    变量上下文
     * @return 解析后的 [title, content]，模板不存在返回 null
     */
    public String[] resolve(String tenantId, String templateCode, String channel,
                             String locale, Map<String, Object> variables) {
        if (templateMapper == null) {
            return null;
        }
        String effectiveTenant = tenantId == null ? "1" : tenantId;
        String effectiveChannel = channel == null ? "INAPP" : channel;
        String effectiveLocale = (locale == null || locale.isBlank()) ? DEFAULT_LOCALE : locale;

        try {
            FlowNotifyTemplateDO tpl = null;
            // 1. 精确匹配 locale
            if (!DEFAULT_LOCALE.equals(effectiveLocale)) {
                tpl = templateMapper.selectEnabledByLocale(
                        effectiveTenant, templateCode, effectiveChannel, effectiveLocale);
            }
            // 2. 降级到默认 locale
            if (tpl == null) {
                tpl = templateMapper.selectEnabledByLocale(
                        effectiveTenant, templateCode, effectiveChannel, DEFAULT_LOCALE);
            }
            // 3. 降级到无 locale 查询（向后兼容旧数据）
            if (tpl == null) {
                tpl = templateMapper.selectEnabled(
                        effectiveTenant, templateCode, effectiveChannel);
            }
            if (tpl == null) {
                return null;
            }
            String title = replacePlaceholders(tpl.getTitle(), variables);
            String content = replacePlaceholders(tpl.getContent(), variables);
            return new String[]{title, content};
        } catch (Exception e) {
            log.warn("[FlowNotifyTemplate] 解析模板失败: code={} channel={} locale={} err={}",
                    templateCode, effectiveChannel, effectiveLocale, e.getMessage());
            return null;
        }
    }

    /**
     * 替换 ${var} 占位符
     */
    private String replacePlaceholders(String template, Map<String, Object> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = variables.get(key);
            String replacement = val == null ? "" : String.valueOf(val);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
