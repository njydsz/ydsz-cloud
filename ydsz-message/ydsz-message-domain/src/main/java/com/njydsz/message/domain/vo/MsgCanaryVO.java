package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 灰度实验视图对象（VO）。
 *
 * <p>用于 Controller 层返回灰度实验的完整信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgCanaryVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 实验唯一标识（主键） */
  private String id;

  /** 灰度实验唯一键 */
  private String canaryKey;

  /** A/B 实验名称 */
  private String experimentName;

  /** 关联模板编码 */
  private String templateCode;

  /** 通道 */
  private String channel;

  /** 总分桶数（默认 100） */
  private Integer bucketTotal;

  /** 命中桶号上限 */
  private Integer bucketSelected;

  /** 当前放量百分比（0~100） */
  private Integer percentage;

  /** 实验组：CONTROL 对照组 / VARIANT 实验组 */
  private String experimentGroup;

  /** 目标指标 */
  private String metricsGoal;

  /** 实验状态 */
  private String status;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
