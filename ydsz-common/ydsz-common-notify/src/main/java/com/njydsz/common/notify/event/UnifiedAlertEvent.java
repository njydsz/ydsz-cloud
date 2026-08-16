package com.njydsz.common.notify.event;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.util.id.IdGenerator;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Collections;
import lombok.Getter;

/**
 * 统一告警事件 — 全局告警事件总线的标准事件载体。
 *
 * <p>所有模块（project / cronjob / agent / workflow / literule）触发告警时， 统一构造此事件并通过 Spring {@code
 * ApplicationEventPublisher} 发布。 消费方通过 {@code @EventListener} 监听， 委托 {@code
 * com.njydsz.common.notify.helper.NotifyHelper} 发送通知。
 *
 * <h3>设计原则</h3>
 *
 * <ul>
 *   <li><b>规则定义与分发解耦</b>：各模块只负责"判断是否告警"并发布事件， 不关心通道路由/去重/重试逻辑
 *   <li><b>统一告警入口</b>：所有告警经过同一事件总线，便于审计/监控/去重
 *   <li><b>幂等键</b>：{@link #alertCode} 作为业务幂等键，相同 code 的告警不会重复分发
 * </ul>
 *
 * <p><b>P2-1</b>：现在继承 {@link DomainEvent}（→ {@code ApplicationEvent}）， 可直接通过 {@code
 * DomainEventPublisher} 发布并由 {@code @EventListener} 消费。
 *
 * <p><b>2026-07-12 迁移</b>：从 ydsz-common-infra 迁移到 ydsz-common-notify， 与通知中心紧耦合（事件最终通过 notify
 * 通道发送），降低跨模块耦合度。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public class UnifiedAlertEvent extends DomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 告警编码（业务幂等键），相同 code 不会重复分发 */
  private final String alertCode;

  /** 告警类型: BUDGET/RISK/EVM/SLA/BENCH/UTILIZATION/QUALITY/JOB_TIMEOUT/JOB_FAILED/OTHER */
  private final String alertType;

  /** 告警等级: YELLOW/RED/NORMAL/INFO */
  private final String alertLevel;

  /** 来源模块: project/cronjob/agent/workflow/literule */
  private final String sourceModule;

  /** 来源业务主键（项目 ID / 任务 ID 等） */
  private final String sourceId;

  /** 来源业务引用（如项目编号） */
  private final String sourceRef;

  /** 告警标题 */
  private final String title;

  /** 告警内容 */
  private final String content;

  /** 目标角色: PM/PMO/GM/CFO/ALL（逗号分隔，可空 → 根据 level 自动解析） */
  private final String targetRole;

  /** 指定接收人 ID 列表（逗号分隔，优先于 targetRole） */
  private final String targetUserIds;

  /** 推送渠道: INAPP/EMAIL/SMS（逗号分隔，可空 → 根据 level 自动解析） */
  private final String pushChannels;

  /** 触发时间 */
  private final LocalDateTime triggeredAt;

  /** 是否为恢复通知（true=告警恢复，false=正常告警） */
  private final boolean recovery;

  public UnifiedAlertEvent(
      String alertCode,
      String alertType,
      String alertLevel,
      String sourceModule,
      String sourceId,
      String sourceRef,
      String title,
      String content,
      String targetRole,
      String targetUserIds,
      String pushChannels,
      LocalDateTime triggeredAt,
      boolean recovery) {
    super(
        IdGenerator.nextIdStr(),
        LocalDateTime.now(),
        DomainEventTypes.UNIFIED_ALERT,
        sourceId,
        sourceModule,
        Collections.emptyMap());
    this.alertCode = alertCode;
    this.alertType = alertType;
    this.alertLevel = alertLevel;
    this.sourceModule = sourceModule;
    this.sourceId = sourceId;
    this.sourceRef = sourceRef;
    this.title = title;
    this.content = content;
    this.targetRole = targetRole;
    this.targetUserIds = targetUserIds;
    this.pushChannels = pushChannels;
    this.triggeredAt = triggeredAt;
    this.recovery = recovery;
  }

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
