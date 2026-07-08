package com.njydsz.pmis.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.DocumentChunkDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档分块 Mapper（P3-1 落地）。
 *
 * <p>向量检索通过 XML 自定义 SQL 实现（pgvector {@code <=>} 余弦距离算子）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkDO> {

    /**
     * 向量检索：按余弦相似度降序返回 top-k 分块。
     *
     * <p>SQL 使用 pgvector 的 {@code <=>} 算子（余弦距离），
     * 相似度 = 1 - 距离。仅检索指定知识库下未删除的分块。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param queryVector     查询向量字符串（如 {@code "[0.1,0.2,...]"}）
     * @param topK            返回条数
     * @return 匹配的分块列表（含距离）
     */
    @Select("SELECT c.*, " +
            "       (1 - (c.embedding <=> CAST(#{queryVector} AS vector))) AS similarity " +
            "FROM pmis_agent_document_chunk c " +
            "WHERE c.knowledge_base_id = #{knowledgeBaseId} " +
            "  AND c.deleted = 0 " +
            "  AND c.embedding IS NOT NULL " +
            "ORDER BY c.embedding <=> CAST(#{queryVector} AS vector) ASC " +
            "LIMIT #{topK}")
    List<DocumentChunkDO> searchByVector(@Param("knowledgeBaseId") String knowledgeBaseId,
                                         @Param("queryVector") String queryVector,
                                         @Param("topK") int topK);
}
