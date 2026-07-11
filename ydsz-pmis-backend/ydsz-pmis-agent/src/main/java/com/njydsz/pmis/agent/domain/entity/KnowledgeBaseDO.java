package com.njydsz.pmis.agent.domain.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent RAG 知识库实体（P3-1 落地）。
 *
 * <p>按租户隔离，一个租户可创建多个知识库（如"项目管理制度库"、"技术规范库"）。
 * 知识库下包含多个文档，文档被分块并向量化后存储到 {@link DocumentChunkDO}。
 *
 * <p>对标 Coze 知识库 / Dify Dataset。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_knowledge_base")
public class KnowledgeBaseDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 知识库名称（同租户下唯一） */
    private String name;

    /** 知识库描述 */
    private String description;

    /** 状态：ACTIVE 可用 / ARCHIVED 归档 */
    private String status;

    /** 文档数量（冗余，文档增删时同步更新） */
    private Integer docCount;

    /** 分块数量（冗余，入库/删除时同步更新） */
    private Integer chunkCount;

    /** Embedding 模型：mock/dashscope/qianfan/openai */
    private String embeddingModel;

    /** 向量维度 */
    private Integer embeddingDim;
}
