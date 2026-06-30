package com.njydsz.pmis.execution.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 预算告警事件监听器
 *
 * <p>当前职责: 异步记录告警日志 (WARN/ERROR);
 * 后续可扩展: 推送通知中心、写入预警表、推送到 RocketMQ 通知主题等.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class BudgetAlertEventListener {

    @Async
    @EventListener
    public void onBudgetAlert(BudgetAlertEvent event) {
        if (event == null) return;
        BudgetAlertEvent.Level level = event.getLevel();
        String template = "[预算告警-{}] 项目[{}-{}] {} 本次 {} 元 | 累计 {} / 预算 {} | 使用率 {}%";
        String levelText = level == null ? "?" : level.name();
        if (level == BudgetAlertEvent.Level.RED) {
            log.error(template,
                    levelText,
                    event.getProjectCode(), event.getProjectName(),
                    event.getBizType(), event.getDelta(),
                    event.getUsedAfter(), event.getBudget(),
                    percent(event.getRatio()));
        } else {
            log.warn(template,
                    levelText,
                    event.getProjectCode(), event.getProjectName(),
                    event.getBizType(), event.getDelta(),
                    event.getUsedAfter(), event.getBudget(),
                    percent(event.getRatio()));
        }
    }

    private static String percent(java.math.BigDecimal ratio) {
        if (ratio == null) return "-";
        return ratio.multiply(java.math.BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
