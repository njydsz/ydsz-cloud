package com.njydsz.common.docs.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 文档分节模型
 *
 * <p>表示文档中的一个逻辑段落，如标题段落、列表、代码块等。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
public class DocumentSection {

  /** 分节类型（heading/paragraph/list/code/link/image 等） */
  private String type;

  /** 标题层级（1-6），仅对 heading 类型有效 */
  private Integer headingLevel;

  /** 分节内容（纯文本） */
  private String content;

  /** 页码（从 1 开始） */
  private Integer pageNumber;

  /** 链接地址，仅对 link 类型有效（如 Markdown 中的 [text](url) 或 HTML 中的 href） */
  private String url;

  /** 是否有序列表；无序列表为 false */
  private Boolean ordered;

  /** 语言标识，仅对 code 类型有效（如 ```java 中的 java） */
  private String language;
}
