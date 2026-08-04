package com.remisoft.common.docs.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 文档分节模型
 * <p>
 * 表示文档中的一个逻辑段落，如标题段落、正文段落、列表等。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@Builder
public class DocumentSection {

    /** 分节类型（heading/paragraph/list/table/image 等） */
    private String type;

    /** 标题层级（1-6），仅对 heading 类型有效 */
    private Integer headingLevel;

    /** 分节内容（纯文本） */
    private String content;

    /** 页码（从 1 开始） */
    private Integer pageNumber;
}
