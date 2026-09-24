package com.njydsz.nextwiki.domain.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 文件-标签关联实体（多对多）。
 *
 * <p>建立 {@link FileNode} 与 {@link Tag} 之间的多对多关系，一条记录表示某个文件被打上了某个标签。
 * 标签作为文件的轻量级分类维度，支持前端按标签筛选、检索文件。
 *
 * <p><b>表名：</b>{@code ydsz_wiki_file_tag}
 *
 * @author ydsz
 * @since 26.09.24
 */@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("ydsz_wiki_file_tag")
public class FileTag extends MpBaseEntity<String> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 文件节点ID */
  private String fileNodeId;

  /** 标签ID */
  private String tagId;
}
