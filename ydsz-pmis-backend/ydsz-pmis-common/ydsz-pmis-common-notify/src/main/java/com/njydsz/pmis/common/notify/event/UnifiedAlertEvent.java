package com.njydsz.pmis.common.notify.event;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 统一告警事件 — 全局告警事件总线的标准事件载体。
 *
 * <p>所有模块（project / cronjob / agent / workflow / literule）触发告警时，
 * 统一构造此事件并通过 Spring {@code ApplicationEventPublisher} 发布。
 * 由 {@code UnifiedAlertDispatcher} 统一消费并委托到 message 模块发送。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>规则定义与分发解耦</b>：各模块只负责"判断是否告警"并发布事件，
 *       不关心通道路由/去重/重试逻辑</li>
 *   <li><b>统一告警入口</b>：所有告警经过同一事件总线，便于审计/监控/去重</li>
 *   <li><b>幂等键</b>：{@link #alertCode} 作为业务幂等键，相同 code 的告警不会重复分发</li>
 * </ul>
 *
 * <p><b>2026-07-12 迁移</b>：从 ydsz-pmis-common-infra 迁移到 ydsz-pmis-common-notify，
 * 与通知中心紧耦合（事件最终通过 notify 通道发送），降低跨模块耦合度。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
public class UnifiedAlertEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 告警编码（业务幂等键），相同 code 不会重复分发 */
    private String alertCode;

    /** 告警类型: BUDGET/RISK/EVM/SLA/BENCH/UTILIZATION/QUALITY/JOB_TIMEOUT/JOB_FAILED/OTHER */
    private String alertType;

    /** 告警等级: YELLOW/RED/NORMAL/INFO */
    private String alertLevel;

    /** 来源模块: project/cronjob/agent/workflow/literule */
    private String sourceModule;

    /** 来源业务主键（项目 ID / 任务 ID 等） */
    private String sourceId;

    /** 来源业务引用（如项目编号） */
    private String sourceRef;

    /** 告警标题 */
    private String title;

    /** 告警内容 */
    private String content;

    /** 目标角色: PM/PMO/GM/CFO/ALL（逗号分隔，可空 → 根据 level 自动解析） */
    private String targetRole;

    /** 指定接收人 ID 列表（逗号分隔，优先于 targetRole） */
    private String targetUserIds;

    /** 推送渠道: INAPP/EMAIL/SMS（逗号分隔，可空 → 根据 level 自动解析） */
    private String pushChannels;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID */
    private String traceId;

    /** 是否为恢复通知（true=告警恢复，false=正常告警） */
    private boolean recovery;

    /**
     * 构建默认的告警编码
     *
     * @param alertType 告警类型
     * @param alertLevel 告警等级
     * @return 告警编码
     */
    public static String buildAlertCode(String alertType, String alertLevel) {
        return alertType + "-" + alertLevel + "-" + System.currentTimeMillis();
    }
}
