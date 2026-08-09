package com.njydsz.literule.server.spi;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认告警动作处理器
 *
 * <p>将规则触发结果转换为 {@link RuleTriggeredEvent} 并发布 Spring 事件，
 * 消费方可通过 {@code @EventListener} 监听此事件，转换为统一告警通知。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class DefaultAlertActionHandler implements RuleActionHandler {

    private final ApplicationEventPublisher eventPublisher;

    public DefaultAlertActionHandler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void handle(List<RuleResult> triggered, RuleContext context) {
        for (RuleResult result : triggered) {
            try {
                RuleTriggeredEvent event = new RuleTriggeredEvent();
                event.setRuleCode(result.getRuleCode());
                event.setRuleName(result.getRuleName());
                event.setSeverity(result.getSeverity() != null ? result.getSeverity().name() : null);
                event.setTitle(result.getTitle());
                event.setDescription(result.getDescription());
                event.setFacts(context != null ? context.getFacts() : null);
                eventPublisher.publishEvent(event);
                log.debug("[LiteRule-Action] 告警事件已发布: ruleCode={}", result.getRuleCode());
            } catch (Exception e) {
                log.warn("[LiteRule-Action] 告警事件发布失败: ruleCode={}, error={}",
                        result.getRuleCode(), e.getMessage());
            }
        }
    }

    /**
     * 规则触发事件
     *
     * <p>消费方通过 {@code @EventListener(RuleTriggeredEvent.class)} 监听，
     * 转换为 {@code UnifiedAlertEvent} 统一发送通知。
     */
    @Data
    public static class RuleTriggeredEvent {
        /** 规则编码 */
        private String ruleCode;
        /** 规则名称 */
        private String ruleName;
        /** 严重度 */
        private String severity;
        /** 标题 */
        private String title;
        /** 描述 */
        private String description;
        /** 事实数据 */
        private Map<String, Object> facts;
    }
}
