package com.njydsz.pmis.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.KnowledgeBaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库 Mapper（P3-1 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseDO> {

    /**
     * 原子递增文档数（文档新增/删除时调用）。
     *
     * @param id    知识库 ID
     * @param delta 增量（+1 或 -1）
     * @return 影响行数
     */
    @Update("UPDATE pmis_agent_knowledge_base " +
            "SET doc_count = GREATEST(doc_count + #{delta}, 0), " +
            "    updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted = 0")
    int incrementDocCount(@Param("id") String id, @Param("delta") int delta);

    /**
     * 原子递增分块数（入库/删除分块时调用）。
     *
     * @param id    知识库 ID
     * @param delta 增量
     * @return 影响行数
     */
    @Update("UPDATE pmis_agent_knowledge_base " +
            "SET chunk_count = GREATEST(chunk_count + #{delta}, 0), " +
            "    updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted = 0")
    int incrementChunkCount(@Param("id") String id, @Param("delta") int delta);
}
