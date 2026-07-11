package com.njydsz.pmis.agent.server.tool;

import com.njydsz.pmis.agent.web.config.RAGProperties;
import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.agent.server.rag.RetrievedChunk;
import com.njydsz.pmis.agent.server.rag.Retriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具（P3-1 落地）。
 *
 * <p>将 RAG 检索能力封装为 {@link AgentTool}，供 ReAct 推理循环调用。
 * LLM 可在推理过程中决策「何时需要查询知识库」，并基于检索结果生成回答。
 *
 * <p>对标 Coze 知识库检索节点 / Dify Retriever Tool。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code knowledgeBaseId} - 知识库 ID（必填）</li>
 *   <li>{@code query} - 查询文本（必填）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Slf4j
@Component
public class KnowledgeSearchTool implements AgentTool {

    private final ObjectProvider<Retriever> retrieverProvider;
    private final RAGProperties properties;

    public KnowledgeSearchTool(ObjectProvider<Retriever> retrieverProvider,
                               RAGProperties properties) {
        this.retrieverProvider = retrieverProvider;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String description() {
        return "检索知识库获取相关知识片段。当需要查阅项目管理制度、技术规范、历史案例等文档时调用。";
    }

    @Override
    public Map<String, Class<?>> parameterSchema() {
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("knowledgeBaseId", String.class);
        schema.put("query", String.class);
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext ctx) {
        // RAG 未启用时直接返回提示
        if (!properties.isEnabled()) {
            return ToolResult.failure("RAG 功能未启用，请配置 pmis.agent.rag.enabled=true");
        }

        Retriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            return ToolResult.failure("Retriever 未配置，无法检索知识库");
        }

        Object kbIdObj = parameters.get("knowledgeBaseId");
        Object queryObj = parameters.get("query");
        if (kbIdObj == null || queryObj == null) {
            return ToolResult.failure("参数缺失：knowledgeBaseId 和 query 均为必填");
        }

        String knowledgeBaseId = String.valueOf(kbIdObj);
        String query = String.valueOf(queryObj);

        try {
            List<RetrievedChunk> chunks = retriever.retrieve(knowledgeBaseId, query);
            if (chunks.isEmpty()) {
                return ToolResult.success("未检索到相关知识片段", Map.of("count", 0));
            }

            // 拼接检索结果为 LLM 可读的文本
            StringBuilder sb = new StringBuilder();
            sb.append("检索到 ").append(chunks.size()).append(" 条相关知识片段：\n\n");
            for (int i = 0; i < chunks.size(); i++) {
                RetrievedChunk chunk = chunks.get(i);
                sb.append("[").append(i + 1).append("] ")
                        .append("(相似度: ").append(formatScore(chunk.getScore())).append(") ")
                        .append(chunk.getContent())
                        .append("\n\n");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("count", chunks.size());
            data.put("query", query);
            data.put("knowledgeBaseId", knowledgeBaseId);

            return ToolResult.success(sb.toString().trim(), data);
        } catch (Exception e) {
            log.error("[KnowledgeSearchTool] 检索失败: kb={} query={}",
                    knowledgeBaseId, query, e);
            return ToolResult.failure("检索失败: " + e.getMessage());
        }
    }

    /**
     * 格式化相似度分数为 2 位小数。
     */
    private static String formatScore(Double score) {
        if (score == null) {
            return "N/A";
        }
        return String.format("%.2f", score);
    }
}
