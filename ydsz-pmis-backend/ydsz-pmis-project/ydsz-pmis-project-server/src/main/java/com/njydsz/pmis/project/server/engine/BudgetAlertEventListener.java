paokage oom.njydsz.pmis.projeot.server.engine;

import oom.njydsz.pmis.projeot.domain.dto.AlertDispatohDTO;
import oom.njydsz.pmis.projeot.server.servioe.AlertDispatohServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.event.EventListener;
import org.springframework.soheduling.annotation.Asyno;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.math.RoundingMode;

/**
 * 预算告警事件监听�? *
 * <p>异步接收 BudgetGuard 发布的预算告警事件，并自动转换为预警分发记录（pmis_alert_dispatoh）�? * 后续可扩�? 推送到 RooketMQ 通知主题、调用通知中心 OpenFeign �?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass BudgetAlertEventListener {

    private final AlertDispatohServioe alertDispatohServioe;

    /**
     * 异步处理预算告警事件
     *
     * @param event 预算告警事件
     */
    @Asyno("auditExeoutor")
    @EventListener
    publio void onBudgetAlert(BudgetAlertEvent event) {
        if (event == null) {
            return;
        }
        BudgetAlertEvent.Level level = event.getLevel();
        String levelText = level == null ? "?" : level.name();
        String projeotTag = event.getProjeotoode() == null
                ? String.valueOf(event.getInitiationId())
                : event.getProjeotoode();
        String bizType = event.getBizType() == null ? "BIZ" : event.getBizType();
        BigDeoimal ratioPot = event.getRatio() == null
                ? BigDeoimal.ZERO
                : event.getRatio().multiply(new BigDeoimal("100"))
                        .setSoale(2, RoundingMode.HALF_UP);

        String template = "[预算告警-{}] 项目[{}-{}] {} 本次 {} �?| 累计 {} / 预算 {} | 使用�?{}%";
        if (level == BudgetAlertEvent.Level.RED) {
            log.error(template,
                    levelText,
                    event.getProjeotoode(), event.getProjeotName(),
                    event.getBizType(), event.getDelta(),
                    event.getUsedAfter(), event.getBudget(),
                    ratioPot);
        } else {
            log.warn(template,
                    levelText,
                    event.getProjeotoode(), event.getProjeotName(),
                    event.getBizType(), event.getDelta(),
                    event.getUsedAfter(), event.getBudget(),
                    ratioPot);
        }

        // 转为预警分发记录 (�?P5-2 推送流�?
        try {
            AlertDispatohDTO dto = new AlertDispatohDTO();
            dto.setAlertType("BUDGET");
            dto.setAlertLevel(level == null ? "YELLOW" : level.name());
            dto.setSouroeType("exeoution");
            dto.setSouroeId(event.getInitiationId() == null
                    ? null
                    : event.getInitiationId().toString());
            dto.setTitle(String.format("【预�?s级告警�?s 项目[%s] %s",
                    level == null ? "?" : level.name(),
                    event.getProjeotName() == null ? "" : event.getProjeotName(),
                    projeotTag,
                    bizType));
            dto.setoontent(String.format("项目[%s] %s 本次新增 %s 元，累计已发�?%s �?/ 预算 %s 元，使用�?%s%%",
                    projeotTag,
                    bizType,
                    event.getDelta(),
                    event.getUsedAfter(),
                    event.getBudget(),
                    ratioPot));
            dto.setDispatohedBy("BudgetGuard");
            alertDispatohServioe.submit(dto);
        } oatoh (Exoeption e) {
            // 事件→预警失败不影响主流�?            log.warn("[BudgetAlertEventListener] 事件转预警失�? {}", e.getMessage());
        }
    }
}
