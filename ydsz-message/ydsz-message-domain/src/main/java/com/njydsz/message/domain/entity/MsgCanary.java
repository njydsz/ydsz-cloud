package com.njydsz.message.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 灰度实验持久化实体，支撑消息模板 A/B 对照实验。
 *
 * <p>对应数据库表 {@code ydsz_msg_canary}。记录灰度实验的完整生命周期：
 * 实验创建、流量分桶、结果记录。canaryKey 为实验唯一标识（格式 canary_{templateCode}_{timestamp}），
 * bucketTotal 划分等宽桶，bucketSelected 控制 VARIANT 组命中范围，
 * experimentGroup 区分 CONTROL/VARIANT，metricsGoal 指定优化目标指标。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合 JPA 继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_canary")
public class MsgCanary extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

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
