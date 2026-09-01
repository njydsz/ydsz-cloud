package com.njydsz.agent.domain.citation;

/**
 * 知识引用（Citation）值对象。
 *
 * <p>表示 Agent 回答中引用的一个知识来源片段，包含溯源信息：
 * 文档 ID、页码/块序号、原始文本摘录、相似度得分。</p>
 *
 * <p>借鉴 MateClaw 的 LLM Wiki 引用抽屉设计——每个回答中的引用
 * 都可以追溯到原始知识库中的具体位置。</p>
 *
 * @param documentId    文档 ID
 * @param documentTitle 文档标题
 * @param chunkIndex    块序号
 * @param textExcerpt   文本摘录
 * @param score         相似度得分
 * @param sourcePath    来源路径
 * @author ydsz-agent
 * @since 26.09.01
 */
public record Citation(
        String documentId,
        String documentTitle,
        int chunkIndex,
        String textExcerpt,
        double score,
        String sourcePath
) {
    /**
     * 创建引用实例。
     *
     * @param documentId     文档 ID
     * @param documentTitle  文档标题
     * @param chunkIndex     块序号
     * @param textExcerpt    文本摘录
     * @param score          相似度得分
     * @param sourcePath     来源路径
     * @return Citation 实例
     */
    public Citation {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if (textExcerpt == null) {
            textExcerpt = "";
        }
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score 必须在 0.0-1.0 之间: " + score);
        }
    }

    /**
     * 创建简化的引用实例（仅含必要字段）。
     *
     * @param documentId  文档 ID
     * @param chunkIndex  块序号
     * @param textExcerpt 文本摘录
     * @param score       相似度得分
     * @return Citation 实例
     */
    public Citation(String documentId, int chunkIndex, String textExcerpt, double score) {
        this(documentId, "", chunkIndex, textExcerpt, score, "");
    }

    /**
     * 获取引用的简短描述（用于前端展示）。
     *
     * @return 引用描述，如 "文档A #3 (相似度 0.85)"
     */
    public String getShortDescription() {
        String title = documentTitle != null && !documentTitle.isBlank()
                ? documentTitle : documentId;
        return String.format("%s #%d (%.2f)", title, chunkIndex, score);
    }
}
