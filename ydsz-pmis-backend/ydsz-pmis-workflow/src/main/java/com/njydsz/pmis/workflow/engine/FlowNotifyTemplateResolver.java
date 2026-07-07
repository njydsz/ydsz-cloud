package com.njydsz.pmis.workflow.engine;

import com.njydsz.pmis.workflow.entity.FlowNotifyTemplateDO;
import com.njydsz.pmis.workflow.mapper.FlowNotifyTemplateMapper;
import lombok.extern.slf4j.Slf4j;
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
 * <p>占位符示例：{@code ${flowName}} / {@code ${nodeName}} / {@code ${assigneeName}} /
 * {@code ${instanceId}} / {@code ${taskId}} / {@code ${operatorName}} / {@code ${reason}} /
 * {@code ${comment}} / {@code ${dueAt}} / {@code ${reminderCount}} / {@code ${maxReminders}}
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Component
public class FlowNotifyTemplateResolver {

    /** ${var} 占位符正则 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");

    private final FlowNotifyTemplateMapper templateMapper;

    public FlowNotifyTemplateResolver(
            org.springframework.beans.factory.ObjectProvider<FlowNotifyTemplateMapper> provider) {
        this.templateMapper = provider.getIfAvailable();
        if (this.templateMapper == null) {
            log.info("[FlowNotifyTemplate] Mapper 不可用，通知将使用硬编码默认值");
        }
    }

    /**
     * 解析模板
     *
     * @param tenantId     租户 ID
     * @param templateCode 模板编码（如 TASK_CREATED）
     * @param channel      通知通道（如 IN_APP）
     * @param variables    变量上下文
     * @return 解析后的 [title, content]，模板不存在返回 null
     */
    public String[] resolve(String tenantId, String templateCode, String channel,
                             Map<String, Object> variables) {
        if (templateMapper == null) {
            return null;
        }
        try {
            FlowNotifyTemplateDO tpl = templateMapper.selectEnabled(
                    tenantId == null ? "1" : tenantId,
                    templateCode,
                    channel == null ? "IN_APP" : channel);
            if (tpl == null) {
                return null;
            }
            String title = replacePlaceholders(tpl.getTitle(), variables);
            String content = replacePlaceholders(tpl.getContent(), variables);
            return new String[]{title, content};
        } catch (Exception e) {
            log.warn("[FlowNotifyTemplate] 解析模板失败: code={} channel={} err={}",
                    templateCode, channel, e.getMessage());
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
