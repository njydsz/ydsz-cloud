package com.njydsz.system.server.service.event;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典变更事件 DTO — Redis Pub/Sub 通道两侧的序列化契约。
 *
 * <p>通过 {@code ydsz:dict:change} 通道广播，所有订阅的 SSE Emitter 收到事件后转发给前端。 字段设计参考 {@link
 * com.njydsz.common.event.api.DomainEvent} 事件契约，聚焦字典变更场景做最小化裁剪。
 *
 * <p><b>序列化方式：</b>JSON（通过 {@link com.njydsz.common.json.YdszJson} 反序列化）， 传输时自动携带操作人（{@link #operatorName}）+ 触发时间戳（{@link
 * #timestamp}）， 供前端渲染变更通知与日志追查使用。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see DictChangeEventPublisher 事件发布器（Publisher 侧序列化）
 * @see com.njyzsz.system.config.DictSseRedisListenerConfig 事件监听器（Subscriber 侧反序列化）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictChangeEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 字典类型编码（必填，唯一标识字典，如 "user_status"） */
  private String dictCode;

  /** 字典项编码（仅字典项变更时填充，字典类型变更时可为 null） */
  private String dictItemCode;

  /** 事件类型：CREATED / UPDATED / DELETED */
  private String eventType;

  /** 操作人 ID（未登录为 SYSTEM） */
  private String operatorId;

  /** 操作人姓名（展示用） */
  private String operatorName;

  /** 事件触发时间戳 */
  private LocalDateTime timestamp;
}
