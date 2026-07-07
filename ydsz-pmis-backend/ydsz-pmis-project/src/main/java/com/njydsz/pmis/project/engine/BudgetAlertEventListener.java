package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.dto.AlertDispatchDTO;
import com.njydsz.pmis.project.service.AlertDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 预算告警事件监听器
 *
 * <p>异步接收 BudgetGuard 发布的预算告警事件，并自动转换为预警分发记录（pmis_alert_dispatch）。
 * 后续可扩展: 推送到 RocketMQ 通知主题、调用通知中心 OpenFeign 等.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetAlertEventListener {

    private final AlertDispatchService alertDispatchService;

    /**
     * 异步处理预算告警事件
     *
     * @param event 预算告警事件
     */
    @Async("auditExecutor")
    @EventListener
    public void onBudgetAlert(BudgetAlertEvent event) {
        if (event == null) {
            return;
        }
        BudgetAlertEvent.Level level = event.getLevel();
        String levelText = level == null ? "?" : level.name();
        String projectTag = event.getProjectCode() == null
                ? String.valueOf(event.getInitiationId())
                : event.getProjectCode();
        String bizType = event.getBizType() == null ? "BIZ" : event.getBizType();
        BigDecimal ratioPct = event.getRatio() == null
                ? BigDecimal.ZERO
                : event.getRatio().multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);

        String template = "[预算告警-{}] 项目[{}-{}] {} 本次 {} 元 | 累计 {} / 预算 {} | 使用率 {}%";
        if (level == BudgetAlertEvent.Level.RED) {
            log.error(template,
                    levelText,
                    event.getProjectCode(), event.getProjectName(),
                    event.getBizType(), event.getDelta(),
                    event.getUsedAfter(), event.getBudget(),
                    ratioPct);
        } else {
            log.warn(template,
                    levelText,
                    event.getProjectCode(), event.getProjectName(),
                    event.getBizType(), event.getDelta(),
                    event.getUsedAfter(), event.getBudget(),
                    ratioPct);
        }

        // 转为预警分发记录 (走 P5-2 推送流程)
        try {
            AlertDispatchDTO dto = new AlertDispatchDTO();
            dto.setAlertType("BUDGET");
            dto.setAlertLevel(level == null ? "YELLOW" : level.name());
            dto.setSourceType("execution");
            dto.setSourceId(event.getInitiationId() == null
                    ? null
                    : event.getInitiationId().toString());
            dto.setTitle(String.format("【预算%s级告警】%s 项目[%s] %s",
                    level == null ? "?" : level.name(),
                    event.getProjectName() == null ? "" : event.getProjectName(),
                    projectTag,
                    bizType));
            dto.setContent(String.format("项目[%s] %s 本次新增 %s 元，累计已发生 %s 元 / 预算 %s 元，使用率 %s%%",
                    projectTag,
                    bizType,
                    event.getDelta(),
                    event.getUsedAfter(),
                    event.getBudget(),
                    ratioPct));
            dto.setDispatchedBy("BudgetGuard");
            alertDispatchService.submit(dto);
        } catch (Exception e) {
            // 事件→预警失败不影响主流程
            log.warn("[BudgetAlertEventListener] 事件转预警失败: {}", e.getMessage());
        }
    }
}
