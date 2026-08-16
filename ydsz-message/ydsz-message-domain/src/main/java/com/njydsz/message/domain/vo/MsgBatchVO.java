package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 消息发送批次视图对象（VO）。
 *
 * <p>用于 Controller 层返回批量发送的进度和统计信息，包含总条数、成功/失败/跳过数、 批次状态及时间线，支撑批量发送运维监控。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgBatchVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 批次记录唯一标识（主键） */
  private String id;

  /** 批次 ID（业务唯一） */
  private String batchId;

  /** 批次名称 */
  private String batchName;

  /** 发送通道 */
  private String channel;

  /** 模板编码 */
  private String templateCode;

  /** 业务类型 */
  private String bizType;

  /** 总条数 */
  private Integer total;

  /** 成功条数 */
  private Integer success;

  /** 失败条数 */
  private Integer failed;

  /** 跳过条数（去重/限流/免打扰） */
  private Integer skipped;

  /** 批次状态（PENDING/RUNNING/COMPLETED/FAILED/CANCELLED） */
  private String status;

  /** 受众来源（MANUAL/TAG/DEPT/FILE） */
  private String audienceSource;

  /** 错误信息 */
  private String errorMessage;

  /** 开始发送时间 */
  private LocalDateTime startedAt;

  /** 完成发送时间 */
  private LocalDateTime completedAt;

  /** 发送人 ID */
  private String senderId;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
