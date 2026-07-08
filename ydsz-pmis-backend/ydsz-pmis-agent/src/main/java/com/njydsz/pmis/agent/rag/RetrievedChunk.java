package com.njydsz.pmis.agent.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 向量检索结果（P3-1 落地）。
 *
 * <p>封装检索到的分块内容与相似度分数，供 RAG 拼接 prompt 使用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分块 ID */
    private String id;

    /** 所属文档 ID */
    private String documentId;

    /** 所属知识库 ID */
    private String knowledgeBaseId;

    /** 分块序号 */
    private Integer chunkIndex;

    /** 分块文本内容 */
    private String content;

    /** 分块 token 数 */
    private Integer tokenCount;

    /** 余弦相似度分数（[0, 1]，1 表示完全相同） */
    private Double score;
}
