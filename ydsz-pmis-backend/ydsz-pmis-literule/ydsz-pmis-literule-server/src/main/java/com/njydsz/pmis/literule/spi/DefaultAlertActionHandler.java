package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.common.notify.event.UnifiedAlertEvent;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 默认告警动作处理器（P1-1 规则与消息通知联动）
 *
 * <p>当规则触发时，将 {@link RuleResult} 转换为 {@link UnifiedAlertEvent}
 * 并通过 Spring {@link ApplicationEventPublisher} 发布。
 * 由 common 模块的 {@code UnifiedAlertDispatcher} 统一消费，
 * 委托到 message 模块发送通知（站内信、邮件、WebSocket 推送等）。
 *
 * <h3>联动链路</h3>
 * <pre>
 * RuleEngine.evaluate
 *   → DefaultAlertActionHandler.onTriggered
 *     → ApplicationEventPublisher.publishEvent(UnifiedAlertEvent)
 *       → UnifiedAlertDispatcher(@EventListener @Async)
 *         → MessageServiceClient Feign → message 模块
 *         → NotificationClient Feign → WebSocket 实时推送
 * </pre>
 *
 * <h3>严重度映射</h3>
 * <ul>
 *   <li>{@code RED} → alertLevel="RED"，通道路由 INAPP+EMAIL，目标角色 PMO/GM/CFO</li>
 *   <li>{@code YELLOW} → alertLevel="YELLOW"，通道路由 INAPP，目标角色 PM/PMO</li>
 *   <li>{@code INFO} → alertLevel="INFO"，通道路由 INAPP，目标角色 PM</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
@Slf4j
public class DefaultAlertActionHandler implements RuleActionHandler {

    private final ApplicationEventPublisher eventPublisher;

    public DefaultAlertActionHandler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onTriggered(List<RuleResult> results, RuleContext context) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (RuleResult result : results) {
            if (!result.isTriggered()) {
                continue;
            }
            try {
                publishAlertEvent(result, context);
            } catch (Exception e) {
                log.warn("[LiteRule-Action] 发布告警事件失败: ruleCode={}, error={}",
                        result.getRuleCode(), e.getMessage());
            }
        }
    }

    @Override
    public String getHandlerId() {
        return "default-alert";
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 将 RuleResult 转换为 UnifiedAlertEvent 并发布
     *
     * <p>直接构造 {@link UnifiedAlertEvent}，由 common 模块的
     * {@code UnifiedAlertDispatcher} 消费并路由到 message 模块。
     */
    private void publishAlertEvent(RuleResult result, RuleContext context) {
        String alertLevel = mapSeverity(result.getSeverity());
        String alertType = mapCategory(result.getCategory());

        UnifiedAlertEvent event = UnifiedAlertEvent.builder()
                .alertCode(result.getRuleCode())
                .alertType(alertType)
                .alertLevel(alertLevel)
                .sourceModule("literule")
                .sourceId(context.getScenario())
                .sourceRef(getStringFact(context, "projectCode"))
                .title(result.getTitle() != null ? result.getTitle() : result.getRuleName())
                .content(result.getDescription() != null ? result.getDescription() : "")
                .triggeredAt(result.getTriggeredAt() != null ? result.getTriggeredAt() : LocalDateTime.now())
                .tenantId(context.getTenantId())
                .traceId(context.getTraceId())
                .recovery(false)
                .build();

        eventPublisher.publishEvent(event);

        if (log.isDebugEnabled()) {
            log.debug("[LiteRule-Action] 告警事件已发布: ruleCode={}, level={}, type={}",
                    result.getRuleCode(), alertLevel, alertType);
        }
    }

    /**
     * 规则严重度 → 告警等级映射
     */
    private String mapSeverity(RuleSeverity severity) {
        if (severity == null) {
            return "INFO";
        }
        return switch (severity) {
            case RED -> "RED";
            case YELLOW -> "YELLOW";
            case INFO -> "INFO";
        };
    }

    /**
     * 规则类别 → 告警类型映射
     */
    private String mapCategory(String category) {
        if (category == null || category.isBlank()) {
            return "OTHER";
        }
        return category.toUpperCase();
    }

    /**
     * 从上下文中安全获取字符串事实
     */
    private String getStringFact(RuleContext context, String key) {
        Object val = context.get(key);
        return val != null ? val.toString() : null;
    }
}
