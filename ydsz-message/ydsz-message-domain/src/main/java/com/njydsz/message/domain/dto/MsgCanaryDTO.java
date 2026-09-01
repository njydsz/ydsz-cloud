package com.njydsz.message.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

/**
 * 灰度实验 DTO（命令请求参数）。
 *
 * <p>用于 Repository 层 CUD 操作的统一入参，不区分 Create / Update：
 * <ul>
 *   <li>创建场景：{@code id} 字段不传</li>
 *   <li>更新场景：传入 {@code id}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
public class MsgCanaryDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 实验唯一标识（主键，更新时传入） */
  private String id;

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

  /** 删除标记 */
  private Boolean deleted;

  /** 创建时间 */
  private java.time.LocalDateTime createdAt;
}
