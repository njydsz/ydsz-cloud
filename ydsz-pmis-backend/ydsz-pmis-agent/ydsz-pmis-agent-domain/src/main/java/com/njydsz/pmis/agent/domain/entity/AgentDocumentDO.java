package com.njydsz.pmis.agent.domain.entity.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent RAG 文档实体（P3-1 落地）。
 *
 * <p>知识库中的文档元数据与原始内容。文档入库时会被分块（chunk）并向量化。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_document")
public class AgentDocumentDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 所属知识库 ID */
    private String knowledgeBaseId;

    /** 文档名称 */
    private String name;

    /** 来源类型：TEXT/MARKDOWN/URL/PDF/DOCX */
    private String sourceType;

    /** 来源 URI（URL 或文件路径） */
    private String sourceUri;

    /** 原始内容（纯文本） */
    private String content;

    /** 分块数量 */
    private Integer chunkCount;

    /** 文档总 token 数 */
    private Integer totalTokens;

    /** 状态：PENDING/INGESTED/FAILED */
    private String status;
}
