package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息轨迹视图对象（VO）。
 *
 * <p>用于返回消息轨迹的完整信息，包含节点类型、状态及耗时等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgTraceVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 轨迹记录唯一标识（主键） */
  private String id;

  /** 消息 ID */
  private String msgId;

  /** 链路追踪 ID */
  private String traceId;

  /** 轨迹节点类型 */
  private String node;

  /** 节点状态（SUCCESS/FAILED/SKIPPED/PENDING） */
  private String status;

  /** 通道 */
  private String channel;

  /** 接收人 */
  private String receiver;

  /** 业务类型 */
  private String bizType;

  /** 业务单据 ID */
  private String bizId;

  /** 模板编码 */
  private String templateCode;

  /** 节点耗时（毫秒） */
  private Long costMs;

  /** 节点描述/错误信息 */
  private String message;

  /** 扩展信息 JSON */
  private String extra;

  /** 节点发生时间 */
  private LocalDateTime eventAt;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
