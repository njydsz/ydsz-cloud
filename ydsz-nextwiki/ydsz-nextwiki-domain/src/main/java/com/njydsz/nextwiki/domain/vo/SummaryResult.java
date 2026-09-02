package com.njydsz.nextwiki.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 摘要结果 VO。
 *
 * <p>定义 AI 生成的摘要结果数据结构，供 server 层和 api 层共享使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
public class SummaryResult implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 文件节点ID */
  private String fileNodeId;

  /** 摘要内容 */
  private String summary;

  /** 摘要类型 */
  private String summaryType;

  /** 内容字数 */
  private Integer wordCount;

  /** 生成时间 */
  private LocalDateTime generatedAt;
}
