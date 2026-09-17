package com.njydsz.agent.domain.model.a2a;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A 协议消息模型。
 *
 * <p>Agent 间通信的基本单元，支持多部分内容（文本、文件、数据）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2aMessage {

  /** 消息角色：user（用户/请求）或 agent（Agent/响应） */
  private A2aMessageRole role;

  /** 消息内容段落 */
  private List<A2aPart> parts;

  /** 扩展元数据 */
  private Map<String, Object> metadata;

  /** A2A 消息角色枚举。 */
  public enum A2aMessageRole {
    /** 用户/请求方 */
    USER("user"),
    /** Agent/响应方 */
    AGENT("agent");

    private final String protocolValue;

    A2aMessageRole(String protocolValue) {
      this.protocolValue = protocolValue;
    }

    public String getProtocolValue() {
      return protocolValue;
    }
  }
}
