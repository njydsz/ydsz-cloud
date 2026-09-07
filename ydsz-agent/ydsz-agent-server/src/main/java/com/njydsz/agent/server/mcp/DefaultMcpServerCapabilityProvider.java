package com.njydsz.agent.server.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.mcp.McpExposedTool;
import com.njydsz.agent.domain.mcp.McpServerCapabilityProvider;
import com.njydsz.agent.domain.mcp.McpServerDescriptor;

/**
 * MCP Server 能力提供者默认实现
 *
 * <p>根据当前 {@link AgentProperties} 中各子系统的启用状态，动态构建 {@link McpExposedTool} 列表，
 * 并通过 MCP {@code tools/list} 对外暴露给外部 MCP Client。
 *
 * <p>当前暴露的工具：
 *
 * <ul>
 *   <li>{@code chat} — 同步对话（LLM 聊天）
 *   <li>{@code execute_agent} — 同步执行 Agent（ReAct / RAG / Plan-Execute / Supervisor / DAG 等）
 *   <li>{@code text2sql} — 自然语言转 SQL 查询
 *   <li>{@code rag_query} — 知识库向量检索
 * </ul>
 *
 * <h3>线程安全</h3>
 *
 * <p>工具列表在首次访问后缓存在 {@code toolsCache} 字段中；配置变更需重启重新加载，
 * 符合 Spring Singleton Bean 生命周期。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMcpServerCapabilityProvider implements McpServerCapabilityProvider {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY_4 = 4;

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY_8 = 8;

  /** MCP 协议版本（对齐 Claude Desktop、Cursor 等主流 Client） */
  private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

  /** 属性配置 */
  private final AgentProperties agentProperties;

  /** 缓存的工具列表（首次构建后复用，避免重复计算） */
  private List<McpExposedTool> toolsCache;

  /** 缓存的描述符 */
  private McpServerDescriptor descriptorCache;

  @Override
  public List<McpExposedTool> listExposedTools() {
    if (toolsCache != null) {
      return toolsCache;
    }
    toolsCache = buildExposedTools();
    log.info("[MCP-Server] 工具列表已构建: toolCount={}", toolsCache.size());
    return toolsCache;
  }

  @Override
  public McpServerDescriptor getDescriptor() {
    if (descriptorCache != null) {
      return descriptorCache;
    }
    AgentProperties.McpServer server = agentProperties.getMcpServer();
    String name = server != null && server.getServerName() != null
        ? server.getServerName() : "ydsz-agent";
    String version = server != null && server.getServerVersion() != null
        ? server.getServerVersion() : "26.09.07";
    descriptorCache = new McpServerDescriptor(
        name,
        "YDSZ 项目管理信息系统智能助手 MCP 服务 — 支持对话、NL2SQL、RAG、Agent 执行",
        version,
        listExposedTools());
    return descriptorCache;
  }

  /**
   * 获取当前 MCP 协议版本号。
   *
   * @return MCP 协议版本字符串
   */
  public String getProtocolVersion() {
    return MCP_PROTOCOL_VERSION;
  }

  /**
   * 根据各子系统启用状态动态构建工具列表。
   *
   * <p>每种子系统仅在对应配置启用时才加入暴露列表，避免暴露不可用工具导致 MCP Client 调用失败。
   *
   * @return 暴露工具列表
   */
  private List<McpExposedTool> buildExposedTools() {
    List<McpExposedTool> tools = new ArrayList<>(COLLECTION_CAPACITY_4);

    // chat 对话工具：始终可用（只要 Agent 模块启用）
    if (agentProperties.isEnabled()) {
      tools.add(buildChatTool());
      tools.add(buildExecuteAgentTool());
    }

    // text2sql 工具：需启用 Text2SQL 子模块
    if (agentProperties.getText2sql() != null && agentProperties.getText2sql().isEnabled()) {
      tools.add(buildText2SqlTool());
    }

    // rag_query 工具：需启用 RAG 子模块
    if (agentProperties.getRag() != null && agentProperties.getRag().isEnabled()) {
      tools.add(buildRagQueryTool());
    }

    return tools;
  }

  /** 构建 chat 对话工具。 */
  private McpExposedTool buildChatTool() {
    Map<String, Object> schema = new LinkedHashMap<>(COLLECTION_CAPACITY_8);
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<>(COLLECTION_CAPACITY_8);
    properties.put("message", Map.of(
        "type", "string",
        "description", "用户输入的消息内容"));
    properties.put("conversationId", Map.of(
        "type", "string",
        "description", "对话 ID（可选，不传则自动创建新对话）"));
    properties.put("systemPrompt", Map.of(
        "type", "string",
        "description", "系统提示词（可选，覆盖默认提示词）"));
    schema.put("properties", properties);
    schema.put("required", List.of("message"));

    return new McpExposedTool(
        "chat",
        "发送一条消息给 YDSZ Agent，同步返回 LLM 对话响应。适用于简单问答、闲聊、无工具调用的场景。",
        schema);
  }

  /** 构建 execute_agent 工具（支持 ReAct、RAG、Plan-Execute、Supervisor、DAG 等 Agent 类型）。 */
  private McpExposedTool buildExecuteAgentTool() {
    Map<String, Object> schema = new LinkedHashMap<>(COLLECTION_CAPACITY_8);
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<>(COLLECTION_CAPACITY_8);
    properties.put("userInput", Map.of(
        "type", "string",
        "description", "用户输入的自然语言任务描述"));
    properties.put("conversationId", Map.of(
        "type", "string",
        "description", "对话上下文 ID（可选，不传则创建新会话）"));
    properties.put("systemPrompt", Map.of(
        "type", "string",
        "description", "覆盖默认的系统提示词（可选）"));
    properties.put("maxIterations", Map.of(
        "type", "integer",
        "description", "最大工具调用轮数（可选，默认 10）"));
    properties.put("enabledTools", Map.of(
        "type", "array",
        "items", Map.of("type", "string"),
        "description", "允许 Agent 调用的工具名白名单（可选，不传则使用全部可用工具）"));
    schema.put("properties", properties);
    schema.put("required", List.of("userInput"));

    return new McpExposedTool(
        "execute_agent",
        "执行 YDSZ Agent 任务，支持多轮工具调用（ReAct Loop）。Agent 会根据任务自动选择和执行可用工具（搜索引擎、数据库、API 等），"
            + "最终返回任务完成结果。适用于需要多步骤推理和工具编排的复杂任务。",
        schema);
  }

  /** 构建 text2sql 自然语言转 SQL 查询工具。 */
  private McpExposedTool buildText2SqlTool() {
    Map<String, Object> schema = new LinkedHashMap<>(COLLECTION_CAPACITY_8);
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<>(COLLECTION_CAPACITY_8);
    properties.put("question", Map.of(
        "type", "string",
        "description", "自然语言描述的数据查询问题，例如：'查询 2024 年所有已完成的项目数量'"));
    properties.put("tenantCode", Map.of(
        "type", "string",
        "description", "多租户隔离的租户编码（可选，不传使用默认租户）"));
    schema.put("properties", properties);
    schema.put("required", List.of("question"));

    return new McpExposedTool(
        "text2sql",
        "将自然语言问题转为 SQL 查询并执行，返回结构化数据结果。支持 Schema 智能召回、可行性评估、语义一致性校验等增强能力。",
        schema);
  }

  /** 构建 rag_query 知识库向量检索工具。 */
  private McpExposedTool buildRagQueryTool() {
    Map<String, Object> schema = new LinkedHashMap<>(COLLECTION_CAPACITY_8);
    schema.put("type", "object");

    Map<String, Object> properties = new LinkedHashMap<>(COLLECTION_CAPACITY_8);
    properties.put("query", Map.of(
        "type", "string",
        "description", "检索查询文本，用于向量相似度匹配知识库文档"));
    properties.put("topK", Map.of(
        "type", "integer",
        "description", "召回的文档数量（可选，默认 5）"));
    properties.put("minScore", Map.of(
        "type", "number",
        "description", "最小相似度阈值 0.0~1.0（可选，默认 0.7）"));
    schema.put("properties", properties);
    schema.put("required", List.of("query"));

    return new McpExposedTool(
        "rag_query",
        "在 YDSZ Agent 知识库中进行向量相似度检索，返回与查询语义相关的文档片段列表。适用于需要知识库辅助回答的场景。",
        schema);
  }
}
