package com.njydsz.agent.domain.model.a2a;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A 协议 Agent 能力卡（AgentCard）。
 *
 * <p>描述一个 A2A Agent 的能力、接口 URL 和认证方式，用于 Agent 发现和能力协商。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2aAgentCard {

  /** Agent 名称 */
  private String name;

  /** Agent 描述 */
  private String description;

  /** Agent A2A 接口 URL（.json 发现端点） */
  private String url;

  /** Agent 版本 */
  private String version;

  /** Agent 能力列表 */
  private List<String> capabilities;

  /** 支持的输入 MIME 类型 */
  private List<String> inputContentTypes;

  /** 支持的输出 MIME 类型 */
  private List<String> outputContentTypes;

  /** 是否需要认证 */
  private boolean requiresAuthentication;
}
