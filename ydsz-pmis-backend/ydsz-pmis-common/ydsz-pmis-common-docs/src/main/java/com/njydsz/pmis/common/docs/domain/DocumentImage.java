package com.njydsz.pmis.common.docs.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 文档图片模型
 * <p>
 * 从文档中提取的嵌入图片信息。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
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

    /** 图片 alt 文本或描述 */
    private String altText;

    /** 页码 */
    private Integer pageNumber;
}
