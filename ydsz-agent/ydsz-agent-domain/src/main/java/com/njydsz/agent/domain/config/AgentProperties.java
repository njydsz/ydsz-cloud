package com.njydsz.agent.domain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import com.njydsz.agent.domain.config.properties.A2aProperties;
import com.njydsz.agent.domain.config.properties.CacheProperties;
import com.njydsz.agent.domain.config.properties.CodeExecutionProperties;
import com.njydsz.agent.domain.config.properties.GuardrailProperties;
import com.njydsz.agent.domain.config.properties.HybridSearchProperties;
import com.njydsz.agent.domain.config.properties.InsightProperties;
import com.njydsz.agent.domain.config.properties.LlmProperties;
import com.njydsz.agent.domain.config.properties.McpProperties;
import com.njydsz.agent.domain.config.properties.McpServerProperties;
import com.njydsz.agent.domain.config.properties.MemoryConsolidationProperties;
import com.njydsz.agent.domain.config.properties.MemoryProperties;
import com.njydsz.agent.domain.config.properties.OcrProperties;
import com.njydsz.agent.domain.config.properties.OtelProperties;
import com.njydsz.agent.domain.config.properties.ProfileProperties;
import com.njydsz.agent.domain.config.properties.PromptTemplateProperties;
import com.njydsz.agent.domain.config.properties.QuotaProperties;
import com.njydsz.agent.domain.config.properties.RagProperties;
import com.njydsz.agent.domain.config.properties.RerankerProperties;
import com.njydsz.agent.domain.config.properties.Text2SqlProperties;
import com.njydsz.agent.domain.config.properties.ToolProperties;
import com.njydsz.agent.domain.config.properties.WebSearchProperties;

/**
 * Agent 配置属性（根配置类）
 *
 * <p>包含 LLM、记忆、RAG、MCP、Text2SQL、缓存、Prompt 模板、护栏、工具等全量子系统的配置定义。
 *
 * <p>YAML 前缀：{@code ydsz.agent}
 *
 * <p>各子配置通过 {@link NestedConfigurationProperty} 绑定，支持 YAML 中的 relaxed binding。
 * 具体字段定义参见各独立配置类（{@code domain.config.properties.*}）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties("ydsz.agent")
public class AgentProperties {

  /** 是否启用 Agent 模块 */
  private boolean isEnabled = true;

  /** 默认系统提示词 */
  private String defaultSystemPrompt =
      "你是 YDSZ 项目管理信息系统的智能助手。你可以帮助用户查询项目信息、分析项目进度、发起审批流程、发送消息通知等。请用中文回答。";

  // ========================= 子模块配置（通过 @NestedConfigurationProperty 绑定） =========================

  /** LLM 配置 */
  @NestedConfigurationProperty
  private LlmProperties llm = new LlmProperties();

  /** 记忆配置 */
  @NestedConfigurationProperty
  private MemoryProperties memory = new MemoryProperties();

  /** RAG 配置 */
  @NestedConfigurationProperty
  private RagProperties rag = new RagProperties();

  /** MCP Client 配置 */
  @NestedConfigurationProperty
  private McpProperties mcp = new McpProperties();

  /** MCP Server 配置（ydsz-agent 自身作为 MCP Server 暴露能力） */
  @NestedConfigurationProperty
  private McpServerProperties mcpServer = new McpServerProperties();

  /** Text2SQL 配置 */
  @NestedConfigurationProperty
  private Text2SqlProperties text2sql = new Text2SqlProperties();

  /** LLM 语义缓存配置 */
  @NestedConfigurationProperty
  private CacheProperties cache = new CacheProperties();

  /** Prompt 模板配置 */
  @NestedConfigurationProperty
  private PromptTemplateProperties promptTemplate = new PromptTemplateProperties();

  /** 护栏配置 */
  @NestedConfigurationProperty
  private GuardrailProperties guardrail = new GuardrailProperties();

  /** 工具调用配置 */
  @NestedConfigurationProperty
  private ToolProperties tool = new ToolProperties();

  /** 配额配置 */
  @NestedConfigurationProperty
  private QuotaProperties quota = new QuotaProperties();

  /** 记忆整合配置 */
  @NestedConfigurationProperty
  private MemoryConsolidationProperties memoryConsolidation = new MemoryConsolidationProperties();

  /** 用户画像配置 */
  @NestedConfigurationProperty
  private ProfileProperties profile = new ProfileProperties();

  /** Reranker 精排配置 */
  @NestedConfigurationProperty
  private RerankerProperties rerankerConfig = new RerankerProperties();

  /** 可观测性配置（OpenTelemetry） */
  @NestedConfigurationProperty
  private OtelProperties otel = new OtelProperties();

  /** BI 洞察报告配置 */
  @NestedConfigurationProperty
  private InsightProperties insight = new InsightProperties();

  /** 代码执行配置 */
  @NestedConfigurationProperty
  private CodeExecutionProperties codeExecution = new CodeExecutionProperties();

  /** OCR 配置 */
  @NestedConfigurationProperty
  private OcrProperties ocr = new OcrProperties();

  /** Web 搜索配置 */
  @NestedConfigurationProperty
  private WebSearchProperties webSearch = new WebSearchProperties();

  /** 混合搜索配置 */
  @NestedConfigurationProperty
  private HybridSearchProperties hybridSearch = new HybridSearchProperties();

  /** A2A 协议配置 */
  @NestedConfigurationProperty
  private A2aProperties a2a = new A2aProperties();
}
