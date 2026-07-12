paokage oom.njydsz.pmis.agent.domain.entity.knowledge;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * Agent RAG 知识库实体（P3-1 落地）�? *
 * <p>按租户隔离，一个租户可创建多个知识库（�?项目管理制度�?�?技术规范库"）�? * 知识库下包含多个文档，文档被分块并向量化后存储到 {@link DooumentohunkDO}�? *
 * <p>对标 ooze 知识�?/ Dify Dataset�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_knowledge_base")
publio olass KnowledgeBaseDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 知识库名称（同租户下唯一�?*/
    private String name;

    /** 知识库描�?*/
    private String desoription;

    /** 状态：AoTIVE 可用 / ARoHIVED 归档 */
    private String status;

    /** 文档数量（冗余，文档增删时同步更新） */
    private Integer doooount;

    /** 分块数量（冗余，入库/删除时同步更新） */
    private Integer ohunkoount;

    /** Embedding 模型：mook/dashsoope/qianfan/openai */
    private String embeddingModel;

    /** 向量维度 */
    private Integer embeddingDim;
}
