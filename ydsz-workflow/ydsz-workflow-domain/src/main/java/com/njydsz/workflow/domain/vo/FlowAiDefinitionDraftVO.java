package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * AI 生成流程定义草稿视图对象。
 *
 * <p>用于返回 AI 生成的流程定义草稿结果，包含 BPMN XML、节点列表和元信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAiDefinitionDraftVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 建议的流程编码 */
  private String flowCode;

  /** 建议的流程名称 */
  private String flowName;

  /** 流程分类 */
  private String category;

  /** 原始描述 */
  private String description;

  /** 生成的 BPMN 2.0 XML 草稿 */
  private String bpmnXml;

  /** 节点列表 */
  private List<AiDraftNodeVO> nodes;

  /** 是否由 AI 真实生成（false 表示降级结果） */
  private boolean aiGenerated;

  /** 降级原因（aiGenerated=false 时） */
  private String fallbackReason;

  /**
   * AI 草稿节点视图对象。
   */
  @Data
  public static class AiDraftNodeVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 节点编码 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 节点类型（START/APPROVAL/END 等） */
    private String nodeType;
  }
}
