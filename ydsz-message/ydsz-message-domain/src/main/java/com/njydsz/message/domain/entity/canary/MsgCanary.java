package com.njydsz.message.domain.entity.canary;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 灰度实验领域实体 — 支撑消息模板 A/B 对照实验。
 *
 * <p>对应数据库表 {@code ydsz_msg_canary}。记录灰度实验的完整生命周期：实验创建、流量分桶、结果记录。
 *
 * <p><b>核心字段语义：</b>
 *
 * <ul>
 *   <li>{@code canaryKey} — 实验唯一标识，格式 {@code canary_{templateCode}_{timestamp}}
 *   <li>{@code bucketTotal} — 总分桶数（默认 100），将流量划分为等宽桶
 *   <li>{@code bucketSelected} — 命中桶数，桶号 {@code < bucketSelected} 的请求归入 VARIANT 组
 *   <li>{@code percentage} — 当前放量百分比（0~100），与 bucketSelected 保持同步
 *   <li>{@code experimentGroup} — 实验组标识：CONTROL（对照组）/ VARIANT（实验组）
 *   <li>{@code metricsGoal} — 目标指标：DELIVERY_RATE（送达率）/ READ_RATE（阅读率）/ CLICK_RATE（点击率）
 *   <li>{@code status} — 实验状态：ACTIVE（运行中）/ PAUSED（已暂停）/ COMPLETED（已结束）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@TableName("ydsz_msg_canary")
public class MsgCanary implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ===== 审计字段 =====

  /** 主键 ID（雪花算法） */
  private String id;

  /** 创建人 ID */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 ID */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 删除标识: false 未删除 / true 已删除 */
  private Boolean deleted;

  // ===== 业务字段 =====

  /** 灰度实验唯一键，格式：canary_{templateCode}_{timestamp} */
  private String canaryKey;

  /** A/B 实验名称 */
  private String experimentName;

  /** 关联模板编码 */
  private String templateCode;

  /** 通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU */
  private String channel;

  /** 总分桶数（默认 100） */
  private Integer bucketTotal;

  /** 命中桶号上限，桶号 < bucketSelected 归入 VARIANT 组 */
  private Integer bucketSelected;

  /** 当前放量百分比（0~100） */
  private Integer percentage;

  /** 实验组：CONTROL 对照组 / VARIANT 实验组 */
  private String experimentGroup;

  /** 目标指标：DELIVERY_RATE 送达率 / READ_RATE 阅读率 / CLICK_RATE 点击率 */
  private String metricsGoal;

  /** 实验状态：ACTIVE 运行中 / PAUSED 已暂停 / COMPLETED 已结束 */
  private String status;
}
