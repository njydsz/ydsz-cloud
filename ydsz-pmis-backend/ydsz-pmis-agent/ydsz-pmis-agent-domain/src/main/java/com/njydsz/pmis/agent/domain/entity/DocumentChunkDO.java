package com.njydsz.pmis.agent.domain.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Agent RAG 文档分块实体（P3-1 落地）。
 *
 * <p>文档被切分后的最小检索单元，包含文本内容与向量表示。
 * 向量字段 {@link #embedding} 使用 pgvector 类型，
 * Java 端以 {@code float[]} 承载，由 MyBatis TypeHandler 转换。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_document_chunk")
public class DocumentChunkDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 所属知识库 ID */
    private String knowledgeBaseId;

    /** 所属文档 ID */
    private String documentId;

    /** 分块序号（同文档内从 0 开始递增） */
    private Integer chunkIndex;

    /** 分块文本内容 */
    private String content;

    /**
     * 向量表示（pgvector 类型）。
     *
     * <p>Java 端以 {@code float[]} 承载：
     * <ul>
     *   <li>写入：{@code float[]} → {@code "[1.0,2.0,3.0]"} 字符串</li>
     *   <li>读取：{@code "[1.0,2.0,3.0]"} → {@code float[]}</li>
     * </ul>
     *
     * <p><b>注意</b>：pgvector 的 vector 类型在 SQL 中表示为
     * {@code '[1.0,2.0,3.0]'} 字符串，MyBatis-Plus 默认无对应 TypeHandler，
     * 这里用 {@code String} 承载，由 Service 层负责序列化/反序列化。
     */
    @TableField("embedding")
    private String embedding;

    /** 分块 token 数 */
    private Integer tokenCount;
}
