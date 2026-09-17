package com.njydsz.agent.domain.model.a2a;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A 协议产出物模型。
 *
 * <p>Agent 完成任务后产生的输出结果（文本/文件/数据）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2aArtifact {

  /** Artifact 唯一 ID */
  private String artifactId;

  /** Artifact 名称 */
  private String name;

  /** Artifact 描述 */
  private String description;

  /** Artifact 内容段落 */
  private List<A2aPart> parts;

  /** 扩展元数据 */
  private Map<String, Object> metadata;
}
