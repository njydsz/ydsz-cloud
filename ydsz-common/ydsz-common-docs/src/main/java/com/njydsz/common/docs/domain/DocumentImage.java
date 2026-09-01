package com.njydsz.common.docs.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 文档图片模型
 *
 * <p>从文档中提取的嵌入图片信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
public class DocumentImage {

  /** 图片格式（png/jpeg/gif 等） */
  private String format;

  /** 图片宽度（像素） */
  private int width;

  /** 图片高度（像素） */
  private int height;

  /** 图片字节大小 */
  private long size;

  /** 图片 URL 或 src 路径 */
  private String url;

  /** 图片 alt 文本或描述 */
  private String altText;

  /** 页码 */
  private Integer pageNumber;
}
