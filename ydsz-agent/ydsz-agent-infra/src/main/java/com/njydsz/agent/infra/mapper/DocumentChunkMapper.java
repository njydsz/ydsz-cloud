package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.agent.domain.entity.DocumentChunk;

/**
 * RAG 文档分块向量 Mapper。
 *
 * <p>对应数据表 <code>ydsz_agt_document_chunk</code>，提供分块向量 CRUD 能力。
 *
 * @author ydsz-team
 * @since 26.10.02
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
}
