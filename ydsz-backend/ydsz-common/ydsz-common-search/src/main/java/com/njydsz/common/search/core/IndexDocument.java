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
 * 索引文档模型
 * <p>
 * 表示一个可被搜索引擎索引的文档，包含文档 ID、类型、标题、内容和扩展字段。
 * 各业务模块通过 {@code SearchProvider.toIndexDocument()} 将实体转换为此模型。
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

    /**
     * 获取所有可搜索文本（拼接 title + subtitle + content + tags）
     */
    public String getSearchableText() {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title);
        }
        if (subtitle != null) {
            sb.append(' ').append(subtitle);
        }
        if (content != null) {
            sb.append(' ').append(content);
        }
        if (tags != null) {
            for (String tag : tags) {
                sb.append(' ').append(tag);
            }
        }
        return sb.toString();
    }

    /**
     * 获取仅标题可搜索文本（title + subtitle）
     */
    public String getTitleSearchableText() {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title);
        }
        if (subtitle != null) {
            sb.append(' ').append(subtitle);
        }
        return sb.toString();
    }
}
