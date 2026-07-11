package com.njydsz.pmis.agent.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置（P3-1 落地）。
 *
 * <p>对标 Coze Knowledge / Dify Dataset 配置。
 * 通过 Nacos 配置 {@code pmis.agent.rag.*} 控制 RAG 行为。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Data
@ConfigurationProperties(prefix = "pmis.agent.rag")
public class RAGProperties {

    /** RAG 功能开关 */
    private boolean enabled = false;

    /** Embedding Provider 选择 */
    private String embeddingProvider = "mock";

    /** 向量存储类型 */
    private String vectorStore = "pgvector";

    /** 分块大小（字符数） */
    private int chunkSize = 500;

    /** 分块重叠（字符数） */
    private int chunkOverlap = 50;

    /** 检索 top-k */
    private int topK = 3;

    /** 最低相似度阈值（低于此分数的检索结果被过滤） */
    private double minScore = 0.3;

    /** 拼接到 prompt 的最大 token 数 */
    private int maxContextTokens = 2000;
}
