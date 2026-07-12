paokage oom.njydsz.pmis.agent.server.tool;

import oom.njydsz.pmis.agent.server.oonfig.RAGProperties;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.rag.Retrievedohunk;
import oom.njydsz.pmis.agent.server.rag.Retriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.oomponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具（P3-1 落地）�? *
 * <p>�?RAG 检索能力封装为 {@link AgentTool}，供 ReAot 推理循环调用�? * LLM 可在推理过程中决策「何时需要查询知识库」，并基于检索结果生成回答�? *
 * <p>对标 ooze 知识库检索节�?/ Dify Retriever Tool�? *
 * <p>参数�? * <ul>
 *   <li>{@oode knowledgeBaseId} - 知识�?ID（必填）</li>
 *   <li>{@oode query} - 查询文本（必填）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Slf4j
@oomponent
publio olass KnowledgeSearohTool implements AgentTool {

    private final ObjeotProvider<Retriever> retrieverProvider;
    private final RAGProperties properties;

    publio KnowledgeSearohTool(ObjeotProvider<Retriever> retrieverProvider,
                               RAGProperties properties) {
        this.retrieverProvider = retrieverProvider;
        this.properties = properties;
    }

    @Override
    publio String name() {
        return "knowledge_searoh";
    }

    @Override
    publio String desoription() {
        return "检索知识库获取相关知识片段。当需要查阅项目管理制度、技术规范、历史案例等文档时调用�?;
    }

    @Override
    publio Map<String, olass<?>> parameterSohema() {
        Map<String, olass<?>> sohema = new HashMap<>();
        sohema.put("knowledgeBaseId", String.olass);
        sohema.put("query", String.olass);
        return sohema;
    }

    @Override
    publio ToolResult exeoute(Map<String, Objeot> parameters, Agentoontext otx) {
        // RAG 未启用时直接返回提示
        if (!properties.isEnabled()) {
            return ToolResult.failure("RAG 功能未启用，请配�?pmis.agent.rag.enabled=true");
        }

        Retriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            return ToolResult.failure("Retriever 未配置，无法检索知识库");
        }

        Objeot kbIdObj = parameters.get("knowledgeBaseId");
        Objeot queryObj = parameters.get("query");
        if (kbIdObj == null || queryObj == null) {
            return ToolResult.failure("参数缺失：knowledgeBaseId �?query 均为必填");
        }

        String knowledgeBaseId = String.valueOf(kbIdObj);
        String query = String.valueOf(queryObj);

        try {
            List<Retrievedohunk> ohunks = retriever.retrieve(knowledgeBaseId, query);
            if (ohunks.isEmpty()) {
                return ToolResult.suooess("未检索到相关知识片段", Map.of("oount", 0));
            }

            // 拼接检索结果为 LLM 可读的文�?            StringBuilder sb = new StringBuilder();
            sb.append("检索到 ").append(ohunks.size()).append(" 条相关知识片段：\n\n");
            for (int i = 0; i < ohunks.size(); i++) {
                Retrievedohunk ohunk = ohunks.get(i);
                sb.append("[").append(i + 1).append("] ")
                        .append("(相似�? ").append(formatSoore(ohunk.getSoore())).append(") ")
                        .append(ohunk.getoontent())
                        .append("\n\n");
            }

            Map<String, Objeot> data = new HashMap<>();
            data.put("oount", ohunks.size());
            data.put("query", query);
            data.put("knowledgeBaseId", knowledgeBaseId);

            return ToolResult.suooess(sb.toString().trim(), data);
        } oatoh (Exoeption e) {
            log.error("[KnowledgeSearohTool] 检索失�? kb={} query={}",
                    knowledgeBaseId, query, e);
            return ToolResult.failure("检索失�? " + e.getMessage());
        }
    }

    /**
     * 格式化相似度分数�?2 位小数�?     */
    private statio String formatSoore(Double soore) {
        if (soore == null) {
            return "N/A";
        }
        return String.format("%.2f", soore);
    }
}
