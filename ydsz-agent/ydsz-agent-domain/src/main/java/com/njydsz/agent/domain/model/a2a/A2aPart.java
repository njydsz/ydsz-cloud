package com.njydsz.agent.domain.model.a2a;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A 协议消息段落模型。
 *
 * <p>消息由多个 Part 组成，支持文本、文件、结构化数据三种类型。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2aPart {

  /** 段落类型 */
  private A2aPartType type;

  /** 文本内容（type=text 时） */
  private String text;

  /** 文件数据（type=file 时） */
  private A2aFilePart file;

  /** 结构化数据（type=data 时） */
  private Object data;

  /** 扩展元数据 */
  private Map<String, Object> metadata;

  /** A2A 段落类型枚举。 */
  public enum A2aPartType {
    /** 纯文本 */
    TEXT("text"),
    /** 文件 */
    FILE("file"),
    /** 结构化数据（JSON） */
    DATA("data");

    private final String protocolValue;

    A2aPartType(String protocolValue) {
      this.protocolValue = protocolValue;
    }

    public String getProtocolValue() {
      return protocolValue;
    }

    public static A2aPartType fromProtocolValue(String value) {
      if (value == null) {
        return TEXT;
      }
      for (A2aPartType t : values()) {
        if (t.protocolValue.equalsIgnoreCase(value)) {
          return t;
        }
      }
      return TEXT;
    }
  }
}
