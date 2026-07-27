package com.njydsz.common.search.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 索引文档模型（引擎无关）
 * <p>
 * 表示一个可被搜索引擎索引的文档，包含文档 ID、类型、标题、内容和扩展字段。
 * 各业务模块通过 {@code SearchProvider.toIndexDocument()} 将实体转换为此模型。
 *
 * <p>各引擎实现自行决定如何处理字段：
 * <ul>
 *   <li>PG：将 title + subtitle + content + tags 拼接为 searchable_text</li>
 *   <li>ES/Solr/OpenSearch：按字段独立建立 mapping</li>
 *   <li>RediSearch：按字段定义 schema</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class IndexDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档唯一 ID */
    private String id;

    /** 实体类型（project/contract/wiki/...） */
    private String type;

    /** 标题（文件名/项目名/合同名等，参与搜索 + 高亮） */
    private String title;

    /** 副标题（客户名/路径等，参与搜索） */
    private String subtitle;

    /** 全文内容（文件正文/描述等，参与搜索 + 高亮） */
    private String content;

    /** 摘要（不参与搜索，用于结果展示） */
    private String snippet;

    /** 标签列表 */
    @Builder.Default
    private List<String> tags = Collections.emptyList();

    /** 状态 */
    private String status;

    /** 跳转路径（前端路由） */
    private String path;

    /** 租户 ID */
    private String tenantId;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private Instant updatedAt;

    /** 扩展字段（参与搜索，但不一定高亮） */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
