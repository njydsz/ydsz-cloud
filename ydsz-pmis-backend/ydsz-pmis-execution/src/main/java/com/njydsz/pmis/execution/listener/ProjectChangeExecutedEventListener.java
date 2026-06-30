package com.njydsz.pmis.execution.listener;

import com.njydsz.pmis.common.event.ProjectChangeExecutedEvent;
import com.njydsz.pmis.execution.service.EvmMeasureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 项目变更执行事件监听器
 *
 * <p>监听 ProjectChangeExecutedEvent, 触发 EVM 基线重算, 避免变更后的
 * 旧基线数据继续作为挣值分析的对照基准.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectChangeExecutedEventListener {

    private final EvmMeasureService evmMeasureService;

    @Async
    @EventListener
    public void onProjectChangeExecuted(ProjectChangeExecutedEvent event) {
        if (event == null || event.getInitiationId() == null) {
            return;
        }
        String reason = "PROJECT_CHANGE:" + event.getChangeCode()
                + "(status=" + (event.getFinalStatusCode() == null ? "?" : event.getFinalStatusCode())
                + ", major=" + Boolean.TRUE.equals(event.getMajorFlag()) + ")";
        try {
            Map<String, Object> r = evmMeasureService.recalculateBaseline(event.getInitiationId(), reason);
            log.info("[EVM] 变更触发基线重算完成: initiation={} change={} result={}",
                    event.getInitiationId(), event.getChangeCode(), r);
        } catch (Exception e) {
            // 基线重算失败不影响主业务流, 仅记录
            log.warn("[EVM] 变更触发基线重算失败: initiation={} change={} err={}",
                    event.getInitiationId(), event.getChangeCode(), e.getMessage());
        }
    }
}
