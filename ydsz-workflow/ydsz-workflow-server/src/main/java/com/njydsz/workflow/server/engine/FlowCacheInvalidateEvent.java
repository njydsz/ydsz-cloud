package com.njydsz.workflow.server.engine;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程定义缓存失效事件。
 *
 * <p>当外部 CDC 工具（Canal / Debezium / DBA 手动改库）检测到 {@code ydsz_flow_node} /
 * {@code ydsz_flow_skip} / {@code ydsz_flow_definition} 表发生变更时，
 * 通过 Spring ApplicationEvent 机制通知本服务失效对应缓存。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>DBA 直接修改节点配置（不走 API）时避免缓存与 DB 不一致</li>
 *   <li>Canal 订阅 binlog 后本地发布此事件，实现 CDC → evict 解耦</li>
 *   <li>运维工具执行批量配置刷新时主动发布</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowCacheInvalidateEvent implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 待失效的流程定义 ID */
  private String definitionId;

  /** 变更源描述（用于审计排查） */
  private String source;

  /** 事件发布时间 */
  private LocalDateTime occurredAt;

  /**
   * 构造缓存失效事件。
   *
   * @param definitionId 流程定义 ID
   * @param source 变更源描述
   */
  public FlowCacheInvalidateEvent(String definitionId, String source) {
    this.definitionId = definitionId;
    this.source = source;
    this.occurredAt = LocalDateTime.now();
  }
}
