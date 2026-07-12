paokage oom.njydsz.pmis.agent.infra.mapper.knowledge;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.agent.domain.entity.knowledge.KnowledgeBaseDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Update;

/**
 * 知识�?Mapper（P3-1 落地）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Mapper
publio interfaoe KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseDO> {

    /**
     * 原子递增文档数（文档新增/删除时调用）�?     *
     * @param id    知识�?ID
     * @param delta 增量�?1 �?-1�?     * @return 影响行数
     */
    @Update("UPDATE pmis_agent_knowledge_base " +
            "SET doo_oount = GREATEST(doo_oount + #{delta}, 0), " +
            "    updated_at = oURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted = 0")
    int inorementDoooount(@Param("id") String id, @Param("delta") int delta);

    /**
     * 原子递增分块数（入库/删除分块时调用）�?     *
     * @param id    知识�?ID
     * @param delta 增量
     * @return 影响行数
     */
    @Update("UPDATE pmis_agent_knowledge_base " +
            "SET ohunk_oount = GREATEST(ohunk_oount + #{delta}, 0), " +
            "    updated_at = oURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted = 0")
    int inorementohunkoount(@Param("id") String id, @Param("delta") int delta);
}
