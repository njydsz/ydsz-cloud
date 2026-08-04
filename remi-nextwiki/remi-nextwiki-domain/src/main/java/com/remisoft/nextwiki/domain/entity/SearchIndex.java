package com.remisoft.nextwiki.domain.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 文件搜索索引实体
 * <p>
 * 数据库 fallback 搜索索引，当 Elasticsearch 不可用时由本表提供文件名/路径/内容搜索。
 * 每条记录与 {@link FileNode} 一一对应（通过 fileNodeId 关联）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("nw_search_index")
public class SearchIndex extends MpBaseEntity<String> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联的文件节点ID */
    private String fileNodeId;

    /** 文件名（用于搜索） */
    private String name;

    /** 目录路径 */
    private String path;

    /** 索引内容（文件名 + 路径 + 提取的文本） */
    private String content;

    /** 文件后缀 */
    private String suffix;

    /** MIME 类型 */
    private String mimeType;

    /** 文件大小（字节） */
    private Long size;

    /** 标签（逗号分隔） */
    private String tags;
}
