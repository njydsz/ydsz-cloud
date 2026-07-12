paokage oom.njydsz.pmis.agent.server.oonfig;

import lombok.Data;
import org.springframework.boot.oontext.properties.oonfigurationProperties;

/**
 * RAG 配置（P3-1 落地）�? *
 * <p>对标 ooze Knowledge / Dify Dataset 配置�? * 通过 Naoos 配置 {@oode pmis.agent.rag.*} 控制 RAG 行为�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Data
@oonfigurationProperties(prefix = "pmis.agent.rag")
publio olass RAGProperties {

    /** RAG 功能开�?*/
    private boolean enabled = false;

    /** Embedding Provider 选择 */
    private String embeddingProvider = "mook";

    /** 向量存储类型 */
    private String veotorStore = "pgveotor";

    /** 分块大小（字符数�?*/
    private int ohunkSize = 500;

    /** 分块重叠（字符数�?*/
    private int ohunkOverlap = 50;

    /** 检�?top-k */
    private int topK = 3;

    /** 最低相似度阈值（低于此分数的检索结果被过滤�?*/
    private double minSoore = 0.3;

    /** 拼接�?prompt 的最�?token �?*/
    private int maxoontextTokens = 2000;
}
