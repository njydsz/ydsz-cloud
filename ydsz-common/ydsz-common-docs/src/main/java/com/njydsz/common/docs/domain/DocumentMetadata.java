package com.njydsz.common.docs.domain;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 文档元数据模型
 *
 * <p>描述文档的属性信息，包括作者、创建时间、修改时间、页数、字数等。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
public class DocumentMetadata {

  /** 文档标题 */
  private String title;

  /** 文档作者 */
  private String author;

  /** 文档主题 */
  private String subject;

  /** 文档关键词 */
  private String keywords;

  /** 创建时间 */
  private LocalDateTime createdTime;

  /** 最后修改时间 */
  private LocalDateTime modifiedTime;

  /** 最后修改人 */
  private String modifiedBy;

  /** 页数 */
  private Integer pageCount;

  /** 字数 */
  private Integer wordCount;

  /** 字符数 */
  private Integer charCount;

  /** 文档语言 */
  private String language;

  /** 文档创建软件 */
  private String creator;

  /** 文档生成工具 */
  private String producer;

  /** 自定义属性 */
  private Map<String, String> customProperties;
}
