package com.njydsz.literule.server.cep;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CEP 引擎状态快照（P1-2 Checkpoint 机制）
 *
 * <p>封装 CEP 引擎的可持久化状态，支持序列化到 Redis/DB/File，实现故障恢复。 包含：
 *
 * <ul>
 *   <li>事件队列（用于恢复滑动/滚动/会话/计数窗口的当前状态）
 *   <li>序列模式匹配进度（避免序列模式重启后从头匹配）
 *   <li>会话窗口最后事件时间戳（恢复会话超时判断）
 *   <li>命中总数（恢复累计统计）
 *   <li>快照时间戳（用于恢复时裁剪过期的窗口事件）
 * </ul>
 *
 * <p>使用方式：
 *
 * <pre>
 * // Checkpoint：保存状态
 * CEPStateSnapshot snapshot = engine.checkpoint();
 * byte[] bytes = serialize(snapshot);  // 写入 Redis/DB
 *
 * // Restore：恢复状态
 * CEPStateSnapshot loaded = deserialize(bytes);
 * engine.restore(loaded);
 * </pre>
 *
 * <h3>恢复语义</h3>
 *
 * <ul>
 *   <li>模式定义不会被覆盖（仅恢复运行时状态，不恢复配置）
 *   <li>事件队列恢复时已自动裁剪窗口外的事件（基于 pattern.window）
 *   <li>序列状态恢复后从中断步骤继续匹配
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CEPStateSnapshot implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 快照时间戳 */
  private Instant snapshotTime;

  /** 命中总数 */
  private long totalHits;

  /**
   * 事件队列快照
   *
   * <p>Map structure: patternId → partitionKey → events
   */
  private Map<String, Map<String, List<CEPEventSnapshot>>> eventQueues;

  /**
   * 序列状态快照
   *
   * <p>Map structure: patternId → partitionKey → sequence state
   */
  private Map<String, Map<String, SequenceStateSnapshot>> sequenceStates;

  /**
   * 会话窗口最后事件时间戳
   *
   * <p>Map structure: patternId → partitionKey → lastEventAt
   */
  private Map<String, Map<String, Instant>> sessionLastEventAt;

  /**
   * CEP 事件快照（可序列化的轻量事件表示）
   *
   * <p>剥离原始 {@link CEPEvent} 中可能不可序列化的字段，仅保留引擎运行所需的最小数据集。
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CEPEventSnapshot implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private String type;
    private String partitionKey;
    private Instant timestamp;
    private Map<String, Object> attributes;
  }

  /**
   * 序列状态快照
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SequenceStateSnapshot implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private int currentStep;
    private Instant lastMatchAt;
    private List<CEPEventSnapshot> matchedEvents;
  }
}
