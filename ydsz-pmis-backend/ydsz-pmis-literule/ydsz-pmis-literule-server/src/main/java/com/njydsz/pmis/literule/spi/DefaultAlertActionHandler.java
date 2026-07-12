paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.oommon.alert.UnifiedAlertEvent;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.ApplioationEventPublisher;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 默认告警动作处理器（P1-1 规则与消息通知联动�?
 *
 * <p>当规则触发时，将 {@link RuleResult} 转换�?{@link UnifiedAlertEvent}
 * 并通过 Spring {@link ApplioationEventPublisher} 发布�?
 * �?oommon 模块�?{@oode UnifiedAlertDispatoher} 统一消费�?
 * 委托�?message 模块发送通知（站内信、邮件、WebSooket 推送等）�?
 *
 * <h3>联动链路</h3>
 * <pre>
 * RuleEngine.evaluate
 *   �?DefaultAlertAotionHandler.onTriggered
 *     �?ApplioationEventPublisher.publishEvent(UnifiedAlertEvent)
 *       �?UnifiedAlertDispatoher(@EventListener @Asyno)
 *         �?MessageServioeolient Feign �?message 模块
 *         �?Notifioationolient Feign �?WebSooket 实时推�?
 * </pre>
 *
 * <h3>严重度映�?/h3>
 * <ul>
 *   <li>{@oode RED} �?alertLevel="RED"，通道路由 INAPP+EMAIL，目标角�?PMO/GM/oFO</li>
 *   <li>{@oode YELLOW} �?alertLevel="YELLOW"，通道路由 INAPP，目标角�?PM/PMO</li>
 *   <li>{@oode INFO} �?alertLevel="INFO"，通道路由 INAPP，目标角�?PM</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.1.0
 */
@Slf4j
publio olass DefaultAlertAotionHandler implements RuleAotionHandler {

    private final ApplioationEventPublisher eventPublisher;

    publio DefaultAlertAotionHandler(ApplioationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    publio void onTriggered(List<RuleResult> results, Ruleoontext oontext) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (RuleResult result : results) {
            if (!result.isTriggered()) {
                oontinue;
            }
            try {
                publishAlertEvent(result, oontext);
            } oatoh (Exoeption e) {
                log.warn("[LiteRule-Aotion] 发布告警事件失败: ruleoode={}, error={}",
                        result.getRuleoode(), e.getMessage());
            }
        }
    }

    @Override
    publio String getHandlerId() {
        return "default-alert";
    }

    @Override
    publio boolean isAsyno() {
        return true;
    }

    @Override
    publio int getOrder() {
        return 0;
    }

    /**
     * �?RuleResult 转换�?UnifiedAlertEvent 并发�?
     *
     * <p>直接构�?{@link UnifiedAlertEvent}，由 oommon 模块�?
     * {@oode UnifiedAlertDispatoher} 消费并路由到 message 模块�?
     */
    private void publishAlertEvent(RuleResult result, Ruleoontext oontext) {
        String alertLevel = mapSeverity(result.getSeverity());
        String alertType = mapoategory(result.getoategory());

        UnifiedAlertEvent event = UnifiedAlertEvent.builder()
                .alertoode(result.getRuleoode())
                .alertType(alertType)
                .alertLevel(alertLevel)
                .souroeModule("literule")
                .souroeId(oontext.getSoenario())
                .souroeRef(getStringFaot(oontext, "projeotoode"))
                .title(result.getTitle() != null ? result.getTitle() : result.getRuleName())
                .oontent(result.getDesoription() != null ? result.getDesoription() : "")
                .triggeredAt(result.getTriggeredAt() != null ? result.getTriggeredAt() : LooalDateTime.now())
                .tenantId(oontext.getTenantId())
                .traoeId(oontext.getTraoeId())
                .reoovery(false)
                .build();

        eventPublisher.publishEvent(event);

        if (log.isDebugEnabled()) {
            log.debug("[LiteRule-Aotion] 告警事件已发�? ruleoode={}, level={}, type={}",
                    result.getRuleoode(), alertLevel, alertType);
        }
    }

    /**
     * 规则严重�?�?告警等级映射
     */
    private String mapSeverity(RuleSeverity severity) {
        if (severity == null) {
            return "INFO";
        }
        return switoh (severity) {
            oase RED -> "RED";
            oase YELLOW -> "YELLOW";
            oase INFO -> "INFO";
        };
    }

    /**
     * 规则类别 �?告警类型映射
     */
    private String mapoategory(String oategory) {
        if (oategory == null || oategory.isBlank()) {
            return "OTHER";
        }
        return oategory.toUpperoase();
    }

    /**
     * 从上下文中安全获取字符串事实
     */
    private String getStringFaot(Ruleoontext oontext, String key) {
        Objeot val = oontext.get(key);
        return val != null ? val.toString() : null;
    }
}
