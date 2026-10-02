package com.njydsz.agent.domain.entity;

import java.io.Serial;
import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.jdbc.entity.MpBaseIdEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RAG 文档分块向量实体。
 *
 * <p>对应数据库表 {@code ydsz_agt_document_chunk}，存储文档切分后的文本块及其 embedding 向量，
 * 供 HybridRetriever（全文检索）和 PgVectorStore（向量检索）共用。
 *
 * <p><b>向量字段：</b>{@link #embedding} 为 {@code vector(1536)} 类型，需 pgvector 扩展支持。
 *
 * <p><b>表特征：</b>该表无 created_by/updated_by 审计人字段，仅含 id 主键，继承 {@link MpBaseIdEntity}。
 *
 * @author ydsz-team
 * @since 26.10.02
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentChunk extends MpBaseEntity<String> {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 所属文档 ID */
  @TableField("document_id")
  private String documentId;

  /** 分块文本内容 */
  @TableField("content")
  private String content;

  /** 文本向量（vector(1536)，用于相似度检索） */
  @TableField("embedding")
  private byte[] embedding;

  /** 分块序号（从 0 开始） */
  @TableField("chunk_index")
  private Integer chunkIndex;

  /** Token 数 */
  @TableField("token_count")
  private Integer tokenCount;

  /** 文档标题（冗余，全文检索展示用） */
  @TableField("document_title")
  private String documentTitle;

  /** 来源标识（文件路径/URL 等） */
  @TableField("source")
  private String source;

  /** 分块元数据 JSONB */
  @TableField("metadata")
  private String metadata;
}
