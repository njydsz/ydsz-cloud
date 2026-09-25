package com.njydsz.workflow.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 流程告警配置属性。
 *
 * <p>配置前缀：{@code ydsz.flow.alert}
 *
 * <p>控制异常检测定时任务的阈值与启停，包括卡住阈值、驳回率阈值、积压阈值。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.flow.alert")
public class FlowAlertProperties {

  /** 是否启用异常告警检测定时任务 */
  private boolean isEnabled = true;

  /** 异常检测定时任务 Cron 表达式（默认每小时执行一次） */
  private String cronExpression = "0 0 * * * ?";

  /** 实例卡住阈值（小时）：RUNNING 状态超过此时间判定为卡住 */
  private int stuckThresholdHours = 48;

  /** 驳回率告警阈值（0~1）：近 7 天驳回率超过此值触发告警 */
  private double rejectRateThreshold = 0.5;

  /** 积压告警阈值：同时超期实例数超过此值触发告警 */
  private int backlogThreshold = 20;

  /** 告警通知接收人（用户 ID 列表），未配置时使用流程管理员 */
  private String notifyChannel = "INTERNAL_MESSAGE";
  // INTERNAL_MESSAGE / EMAIL / SMS / WEBHOOK
}
