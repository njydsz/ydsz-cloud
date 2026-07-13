package com.njydsz.pmis.project.server.listener;

import com.njydsz.pmis.project.domain.event.ProjectChangeExecutedEvent;
import com.njydsz.pmis.project.domain.dto.AlertDispatchDTO;
import com.njydsz.pmis.project.server.service.AlertDispatchService;
import com.njydsz.pmis.project.server.service.EvmMeasureService;
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
 * <p>基线重算失败时: 记录 WARN 日志, 并发布 RED 级告警 (走 AlertDispatchService),
 * 避免 EVM 异常悄无声息地扩散到下游报表/CPI/SPI 计算.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectChangeExecutedEventListener {

    private final EvmMeasureService evmMeasureService;
    private final AlertDispatchService alertDispatchService;

    /**
     * 处理项目变更执行事件
     *
     * <p>异步触发 EVM 基线重算；失败时发布 RED 级告警，不影响主业务流。
     *
     * @param event 项目变更执行事件
     */
    @Async("auditExecutor")
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
            // 基线重算失败不影响主业务流, 仅记录 + 告警
            log.warn("[EVM] 变更触发基线重算失败: initiation={} change={} err={}",
                    event.getInitiationId(), event.getChangeCode(), e.getMessage());
            publishBaselineRecalcAlert(event, e);
        }
    }

    /**
     * 基线重算失败 → RED 级告警, 由 AlertDispatchService 根据 level 解析目标角色 (PMO/GM/CFO)
     */
    private void publishBaselineRecalcAlert(ProjectChangeExecutedEvent event, Exception cause) {
        try {
            AlertDispatchDTO dto = new AlertDispatchDTO();
            dto.setAlertType("EVM");
            dto.setAlertLevel("RED");
            dto.setSourceType("execution");
            dto.setSourceId(event.getInitiationId() == null
                    ? null
                    : event.getInitiationId().toString());
            String tag = event.getChangeCode() == null
                    ? String.valueOf(event.getInitiationId())
                    : event.getChangeCode();
            dto.setTitle(String.format("【EVM基线重算失败】变更[%s] 项目[%s]",
                    tag,
                    event.getInitiationId()));
            dto.setContent(String.format(
                    "项目变更[%s] (initiationId=%s, type=%s, major=%s) 触发 EVM 基线重算失败: %s. " +
                            "请相关人员手动重算或回滚变更, 避免 EVM 数据失真扩散.",
                    event.getChangeCode(),
                    event.getInitiationId(),
                    event.getChangeType(),
                    Boolean.TRUE.equals(event.getMajorFlag()),
                    cause == null ? "unknown" : cause.getMessage()));
            dto.setDispatchedBy("ProjectChangeExecutedEventListener");
            alertDispatchService.submit(dto);
            log.info("[EVM] 基线重算失败告警已提交: change={} initiation={}",
                    event.getChangeCode(), event.getInitiationId());
        } catch (Exception alertEx) {
            // 告警发布失败不影响主流程, 仅记录
            log.warn("[EVM] 基线重算失败告警发布失败: change={} initiation={} err={}",
                    event.getChangeCode(), event.getInitiationId(), alertEx.getMessage());
        }
    }
}
