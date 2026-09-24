package com.njydsz.nextwiki.domain.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 标签实体。
 *
 * <p>支持对文件/文件夹打标签，用于知识库分类和检索。标签可由用户手动创建（{@code manual}类型）或由系统
 * 基于文档内容自动推荐（{@code auto}类型），亦可由系统预设（{@code system}类型）。
 * 每个标签拥有唯一名称（{@link #name}）和颜色（{@link #color}，十六进制如 #1890ff），
 * 通过 {@link com.njydsz.nextwiki.domain.entity.FileTag} 与文件节点建立多对多关联。
 *
 * <p><b>表名：</b>{@code ydsz_wiki_tag}
 *
 * @author ydsz
 * @since 26.09.24
 */@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("ydsz_wiki_tag")
public class Tag extends MpBaseEntity<String> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 标签名称 */
  private String name;

  /** 标签颜色（十六进制颜色码，如 #1890ff） */
  private String color;

  /** 标签类型：manual（手动）/ auto（自动推荐）/ system（系统预设） */
  private String type;

  /** 使用次数（文件关联数） */
  private Integer usageCount;
}
