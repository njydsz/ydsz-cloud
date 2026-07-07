package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 告警通知器抽象基类（P5 告警 + 监控）。
 *
 * <p>提供告警文案构建、通道开关判断等通用工具方法，具体通道实现继承本类。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class AbstractAlertNotifier implements AlertNotifier {

    /** 时间格式化器（用于告警文案） */
    protected static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 构建告警标题。
     *
     * @param context 告警上下文
     * @param rule    告警规则
     * @return 标题字符串
     */
    protected String buildTitle(AlertContext context, JobAlertRuleDO rule) {
        return String.format("[%s] %s - %s",
                rule.getAlertLevel(),
                rule.getAlertType(),
                context.jobName() != null ? context.jobName() : (context.jobKey() != null ? context.jobKey() : "全局告警"));
    }

    /**
     * 构建告警内容（Markdown 格式，适用于钉钉/企微/邮件）。
     *
     * @param context 告警上下文
     * @param rule    告警规则
     * @return Markdown 内容
     */
    protected String buildContent(AlertContext context, JobAlertRuleDO rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 告警详情\n\n");
        sb.append("| 字段 | 值 |\n|------|----|\n");
        sb.append("| 规则名称 | ").append(rule.getRuleName()).append(" |\n");
        sb.append("| 告警类型 | ").append(rule.getAlertType()).append(" |\n");
        sb.append("| 告警级别 | ").append(rule.getAlertLevel()).append(" |\n");
        if (context.jobKey() != null) {
            sb.append("| 任务 KEY | ").append(context.jobKey()).append(" |\n");
        }
        if (context.jobName() != null) {
            sb.append("| 任务名称 | ").append(context.jobName()).append(" |\n");
        }
        if (context.triggerValue() != null) {
            sb.append("| 触发值 | ").append(context.triggerValue()).append(" |\n");
        }
        if (rule.getThreshold() != null) {
            sb.append("| 阈值 | ").append(rule.getThreshold()).append(" |\n");
        }
        if (context.errorMessage() != null) {
            sb.append("| 错误信息 | ").append(escapeMarkdown(context.errorMessage())).append(" |\n");
        }
        if (context.triggerLogId() != null) {
            sb.append("| 任务日志 ID | ").append(context.triggerLogId()).append(" |\n");
        }
        if (context.traceId() != null) {
            sb.append("| 链路追踪 ID | ").append(context.traceId()).append(" |\n");
        }
        sb.append("| 触发时间 | ").append(LocalDateTime.now().format(TIME_FORMATTER)).append(" |\n");
        return sb.toString();
    }

    /**
     * 转义 Markdown 特殊字符（避免破坏表格渲染）。
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
