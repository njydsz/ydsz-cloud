package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Data;

/**
 * AI 翻译结果视图对象。
 *
 * <p>用于返回流程定义的翻译结果。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowAiTranslationResultVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程定义 ID */
  private String definitionId;

  /** 目标语言代码 */
  private String targetLang;

  /** 翻译后的流程名称 */
  private String flowName;

  /** 翻译后的节点名称映射（nodeCode -> nodeName） */
  private Map<String, String> nodeNames;

  /** 是否经过 AI 翻译（false 表示降级结果） */
  private boolean translated;

  /** 降级原因（translated=false 时） */
  private String fallbackReason;
}
