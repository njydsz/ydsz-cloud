package com.njydsz.message.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 消息轨迹记录实体，记录消息从接入到投递全链路的每个关键节点。
 *
 * <p>对应数据库表 {@code ydsz_msg_trace}。每条消息在每个关键节点
 * （接收、校验、路由、渲染、投递、回执等）产生一条轨迹记录，
 * 通过 msgId 关联、按 eventAt 时间顺序串联形成完整链路，支撑端到端消息追踪与可视化。
 *
 * @author ydsz
 * @since 26.09.24
 */// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合泛型父类继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_trace")
public class MsgTrace extends MpBaseIdEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 消息 ID（关联 ydsz_msg_log.msg_id） */
  private String msgId;

  /** 链路追踪 ID（关联 ydsz_msg_log.trace_id，用于跨服务链路串联） */
  private String traceId;

  /** 轨迹节点类型 */
  private String node;

  /** 节点状态: SUCCESS / FAILED / SKIPPED / PENDING */
  private String status;

  /** 通道: SMS/EMAIL/PUSH/...（节点关联的通道，部分节点如 RECEIVED 无通道则为 null） */
  private String channel;

  /** 接收人（脱敏后的） */
  private String receiver;

  /** 业务类型 */
  private String bizType;

  /** 业务单据 ID */
  private String bizId;

  /** 模板编码 */
  private String templateCode;

  /** 节点耗时（毫秒） */
  private Long costMs;

  /** 节点描述 / 错误信息 */
  private String message;

  /** 扩展信息 JSON（节点附加数据，如路由规则 ID、降级链、灰度配置等） */
  private String extra;

  /** 节点发生时间 */
  private LocalDateTime eventAt;
}
