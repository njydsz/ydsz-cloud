package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认告警动作处理器（P1-1 规则与消息通知联动）
 *
 * <p>当规则触发时，将 {@link RuleResult} 转换为 {@code UnifiedAlertEvent}
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
 * <p>使用条件：classpath 中存在 {@code UnifiedAlertEvent} 类（由 ydsz-pmis-common 提供）。
 * 若 common 模块不在 classpath，此 handler 不装配，不影响规则引擎核心功能。
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
     * <p>使用反射式发布，避免对 UnifiedAlertEvent 类的硬依赖，
     * 使得 literule 在缺少 common 模块时仍能编译运行。
     */
    private void publishAlertEvent(RuleResult result, RuleContext context) {
        String alertLevel = mapSeverity(result.getSeverity());
        String alertType = mapCategory(result.getCategory());

        // 构建事件数据 Map（与 UnifiedAlertEvent 字段对应）
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("alertCode", result.getRuleCode());
        eventData.put("alertType", alertType);
        eventData.put("alertLevel", alertLevel);
        eventData.put("sourceModule", "literule");
        eventData.put("sourceId", context.getScenario());
        eventData.put("sourceRef", getStringFact(context, "projectCode"));
        eventData.put("title", result.getTitle() != null ? result.getTitle() : result.getRuleName());
        eventData.put("content", result.getDescription() != null ? result.getDescription() : "");
        eventData.put("triggeredAt", result.getTriggeredAt() != null ? result.getTriggeredAt().toString() : "");

        // 发布 Spring ApplicationEvent
        // 使用 Map 作为事件载体，由 UnifiedAlertDispatcher 或适配器消费
        eventPublisher.publishEvent(new RuleTriggeredEvent(eventData));

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

    /**
     * 规则触发事件（Spring ApplicationEvent 载体）
     *
     * <p>当 common 模块存在 {@code UnifiedAlertEvent} 时，
     * 可通过 {@code @EventListener} 适配器将此事件转换为 {@code UnifiedAlertEvent} 重新发布。
     * 当 common 模块不存在时，消费方可直接监听此事件。
     */
    public static class RuleTriggeredEvent {
        private final Map<String, Object> data;

        public RuleTriggeredEvent(Map<String, Object> data) {
            this.data = data;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public String getAlertCode() {
            return (String) data.get("alertCode");
        }

        public String getAlertLevel() {
            return (String) data.get("alertLevel");
        }

        public String getAlertType() {
            return (String) data.get("alertType");
        }

        public String getTitle() {
            return (String) data.get("title");
        }

        public String getContent() {
            return (String) data.get("content");
        }

        public String getSourceModule() {
            return (String) data.get("sourceModule");
        }

        public String getSourceId() {
            return (String) data.get("sourceId");
        }

        public String getSourceRef() {
            return (String) data.get("sourceRef");
        }
    }
}
