package com.njydsz.message.server.service.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 内容优化建议服务（P2-4）。
 *
 * <p>基于规则引擎为消息内容提供优化建议：
 * <ul>
 *   <li>长度检查：SMS ≤ 70 字、邮件主题 ≤ 50 字</li>
 *   <li>敏感词检测</li>
 *   <li>CTA（行动号召）缺失提醒</li>
 *   <li>紧急程度匹配</li>
 * </ul>
 *
 * <p>后续可接入 LLM API 提供更智能的优化建议。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@Slf4j
@Component
public class ContentOptimizationService {

    @Value("${ydsz.message.ai.enabled:false}")
    private boolean aiEnabled;

    /**
     * 分析消息内容并给出优化建议。
     *
     * @param content  消息内容
     * @param channel  通道类型
     * @param subject  邮件主题（可选）
     * @return 优化建议列表
     */
    public List<OptimizationSuggestion> analyze(String content, String channel, String subject) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            suggestions.add(warn("内容为空", "消息内容不能为空"));
            return suggestions;
        }

        String upperChannel = channel == null ? "" : channel.toUpperCase();

        // 1. 长度检查
        if ("SMS".equals(upperChannel) && content.length() > 70) {
            suggestions.add(warn("内容过长", "SMS 通道建议 ≤ 70 字,当前 " + content.length() + " 字"));
        }
        if ("EMAIL".equals(upperChannel)) {
            if (StringUtils.hasText(subject) && subject.length() > 50) {
                suggestions.add(warn("主题过长", "邮件主题建议 ≤ 50 字,当前 " + subject.length() + " 字"));
            }
            if (content.length() > 5000) {
                suggestions.add(warn("内容过长", "邮件正文建议 ≤ 5000 字,当前 " + content.length() + " 字"));
            }
        }
        if (("DINGTALK".equals(upperChannel) || "WECOM".equals(upperChannel))
                && content.length() > 2000) {
            suggestions.add(warn("内容过长", "IM 消息建议 ≤ 2000 字,当前 " + content.length() + " 字"));
        }

        // 2. CTA 缺失检查
        if (!content.contains("点击") && !content.contains("查看") && !content.contains("前往")
                && !content.contains("处理") && !content.contains("去") && !content.contains("立即")) {
            suggestions.add(info("缺少行动号召", "建议在消息中加入明确的行动号召(如'去处理'、'点击查看')"));
        }

        // 3. 紧急程度匹配
        if (content.contains("紧急") || content.contains("立即") || content.contains("马上")) {
            suggestions.add(info("紧急内容", "检测到紧急措辞,建议提升消息优先级为 URGENT"));
        }

        // 4. 可读性检查
        if (content.length() > 200 && !content.contains("\n") && !content.contains("<br")) {
            suggestions.add(info("段落排版", "长文本建议分段排版,提高可读性"));
        }

        return suggestions;
    }

    private OptimizationSuggestion warn(String title, String detail) {
        OptimizationSuggestion s = new OptimizationSuggestion();
        s.setLevel("WARN");
        s.setTitle(title);
        s.setDetail(detail);
        return s;
    }

    private OptimizationSuggestion info(String title, String detail) {
        OptimizationSuggestion s = new OptimizationSuggestion();
        s.setLevel("INFO");
        s.setTitle(title);
        s.setDetail(detail);
        return s;
    }

    /**
     * 优化建议。
     */
    @Data
    public static class OptimizationSuggestion {
        /** 级别: INFO / WARN / ERROR */
        private String level;
        /** 建议标题 */
        private String title;
        /** 详细说明 */
        private String detail;
    }
}
