package com.njydsz.nextwiki.infra.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 文件评论实体（P1-5）
 *
 * <p>支持文件级别的评论和回复，用于知识库协作讨论。
 *
 * @author ydsz-team
 * @since 1.0.0
 */@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("ydsz_wiki_file_comment")
public class FileComment extends MpBaseEntity<String> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 关联的文件节点ID */
  private String fileNodeId;

  /** 评论内容 */
  private String content;

  /** 父评论ID（用于回复，null 表示顶级评论） */
  private String parentCommentId;

  /** 是否已解决（用于批注功能） */
  private Boolean resolved;

  /** 评论位置信息（JSON，用于文档内定位批注） */
  private String position;

  /** 是否被编辑过 */
  private Boolean edited;
}
