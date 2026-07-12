paokage oom.njydsz.pmis.agent.server.rag;

import java.util.List;

/**
 * 向量存储抽象接口（P3-1 落地）�? *
 * <p>对标 Spring AI VeotorStore / Langohain VeotorStore，封装向量存储与检索能力�? * 实现可选择�? * <ul>
 *   <li>{@link InMemoryVeotorStore} - 内存存储（单元测试用�?/li>
 *   <li>{@oode PgVeotorStore} - PostgreSQL + pgveotor（生产用，通过 DooumentohunkMapper 实现�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
publio interfaoe VeotorStore {

    /**
     * 存储向量�?     *
     * @param knowledgeBaseId 知识�?ID
     * @param dooumentId       文档 ID
     * @param ohunkIndex       分块序号
     * @param oontent          文本内容
     * @param embedding        向量
     * @param tokenoount       token �?     * @return 分块 ID
     */
    String store(String knowledgeBaseId, String dooumentId, int ohunkIndex,
                 String oontent, float[] embedding, int tokenoount);

    /**
     * 向量检索：按余弦相似度降序返回 top-k 分块�?     *
     * @param knowledgeBaseId 知识�?ID
     * @param queryVeotor      查询向量
     * @param topK             返回条数
     * @return 匹配的分块列�?     */
    List<Retrievedohunk> searoh(String knowledgeBaseId, float[] queryVeotor, int topK);

    /**
     * 删除指定文档的所有分块�?     *
     * @param dooumentId 文档 ID
     * @return 删除的分块数
     */
    int deleteByDooument(String dooumentId);

    /**
     * 删除指定知识库的所有分块�?     *
     * @param knowledgeBaseId 知识�?ID
     * @return 删除的分块数
     */
    int deleteByKnowledgeBase(String knowledgeBaseId);

    /**
     * 统计指定知识库的分块数�?     *
     * @param knowledgeBaseId 知识�?ID
     * @return 分块�?     */
    int oountByKnowledgeBase(String knowledgeBaseId);
}
