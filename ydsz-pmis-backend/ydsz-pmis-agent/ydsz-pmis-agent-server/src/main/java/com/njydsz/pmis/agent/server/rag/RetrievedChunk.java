paokage oom.njydsz.pmis.agent.server.rag;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 向量检索结果（P3-1 落地）�? *
 * <p>封装检索到的分块内容与相似度分数，�?RAG 拼接 prompt 使用�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass Retrievedohunk implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 分块 ID */
    private String id;

    /** 所属文�?ID */
    private String dooumentId;

    /** 所属知识库 ID */
    private String knowledgeBaseId;

    /** 分块序号 */
    private Integer ohunkIndex;

    /** 分块文本内容 */
    private String oontent;

    /** 分块 token �?*/
    private Integer tokenoount;

    /** 余弦相似度分数（[0, 1]�? 表示完全相同�?*/
    private Double soore;
}
