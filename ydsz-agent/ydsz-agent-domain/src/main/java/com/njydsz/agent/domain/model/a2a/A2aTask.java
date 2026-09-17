package com.njydsz.agent.domain.model.a2a;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A 协议 Task 模型（映射 A2A 规范 Task 对象）。
 *
 * <p>A2A（Agent-to-Agent）协议的核心工作单元。一个 Task 代表 Agent 处理的一次请求， 具有生命周期状态流转（submitted → working → completed / failed）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2aTask {

  /** Task 唯一 ID（由 A2A Server 生成） */
  private String id;

  /** 会话 ID（关联多个 Task 到同一上下文） */
  private String contextId;

  /** 当前任务状态 */
  private A2aTaskStatus status;

  /** 任务产生的输出消息序列（按时间排序） */
  private List<A2aMessage> history;

  /** 任务产生的 Artifact（文件/数据产出物） */
  private List<A2aArtifact> artifacts;

  /** 任务元数据（扩展属性） */
  private Map<String, Object> metadata;

  /** 任务创建时间 */
  private LocalDateTime createdAt;

  /** 任务最后更新时间 */
  private LocalDateTime updatedAt;

  /**
   * A2A 任务状态枚举。
   */
  public enum A2aTaskStatus {
    /** 已提交 */
    SUBMITTED("submitted"),
    /** 处理中 */
    WORKING("working"),
    /** 需要用户输入 */
    INPUT_REQUIRED("input-required"),
    /** 已完成 */
    COMPLETED("completed"),
    /** 已取消 */
    CANCELED("canceled"),
    /** 失败 */
    FAILED("failed"),
    /** 拒绝 */
    REJECTED("rejected"),
    /** 需要认证 */
    AUTH_REQUIRED("auth-required"),
    /** 未知 */
    UNKNOWN("unknown");

    private final String protocolValue;

    A2aTaskStatus(String protocolValue) {
      this.protocolValue = protocolValue;
    }

    public String getProtocolValue() {
      return protocolValue;
    }

    /** 是否为终态（不再接受更新）。
     *
     * @return 如果是终态（COMPLETED / FAILED / CANCELED / REJECTED）则返回 true
     */
    public boolean isTerminal() {
      return this == COMPLETED || this == FAILED || this == CANCELED || this == REJECTED;
    }

    public static A2aTaskStatus fromProtocolValue(String value) {
      if (value == null) {
        return UNKNOWN;
      }
      for (A2aTaskStatus s : values()) {
        if (s.protocolValue.equalsIgnoreCase(value)) {
          return s;
        }
      }
      return UNKNOWN;
    }
  }
}
