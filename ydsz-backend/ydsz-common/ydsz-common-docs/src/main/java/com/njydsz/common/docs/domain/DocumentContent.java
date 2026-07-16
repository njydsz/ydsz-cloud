package com.njydsz.common.docs.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 文档内容模型
 * <p>
 * 文档解析后的完整结果，包含文本内容、结构化分节、表格、图片等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class DocumentContent {

    /** 纯文本内容（所有段落拼接） */
    private String text;

    /** 文档分节列表 */
    private List<DocumentSection> sections;

    /** 文档表格列表 */
    private List<DocumentTable> tables;

    /** 文档图片列表 */
    private List<DocumentImage> images;

    /** 文档元数据 */
    private DocumentMetadata metadata;

    /** 文档总字符数 */
    private int totalChars;

    /** 文档总页数 */
    private int totalPages;
}
