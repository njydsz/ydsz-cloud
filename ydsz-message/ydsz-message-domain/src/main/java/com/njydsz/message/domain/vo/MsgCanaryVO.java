package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 灰度桶视图对象（VO）。
 *
 * <p>用于 Controller 层返回消息灰度发布的桶配置信息，包含分桶总数、选中桶、 灰度比例及实验模板配置，支撑消息 A/B 测试。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgCanaryVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 灰度配置唯一标识（主键） */
  private String id;

  /** 灰度分桶键 */
  private String canaryKey;

  /** 分桶总数 */
  private Integer bucketTotal;

  /** 选中桶（灰度流量分配到的桶号） */
  private String bucketSelected;

  /** 灰度比例（0~100） */
  private Integer percentage;

  /** 实验模板编码 */
  private String experimentTemplateCode;

  /** 实验通道 */
  private String experimentChannel;

  /** 状态（ACTIVE/PAUSED/COMPLETED） */
  private String status;

  /** 描述 */
  private String description;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
