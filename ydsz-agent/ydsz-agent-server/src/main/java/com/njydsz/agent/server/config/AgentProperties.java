package com.njydsz.agent.server.config;.config
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.agent.domain.gateway.PromptTemplateProvider;

/**
 * Agent 模块配置属性
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.agent")
public class AgentProperties {

  /** 是否启用 Agent 模块 */
  private boolean enabled = true;

  /** 默认系统提示词 */
  private String defaultSystemPrompt =
      "你是 YDSZ 项目管理信息系统的智能助手。你可以帮助用户查询项目信息、分析项目进度、发起审批流程、发送消息通知等。请用中文回答。";

  /** LLM 配置 */
  private Llm llm = new Llm();

  /** 记忆配置 */
  private Memory memory = new Memory();

  /** RAG 配置 */
  private Rag rag = new Rag();

  /** MCP 配置 */
  private Mcp mcp = new Mcp();

  /** Text2SQL 配置 */
  private Text2Sql text2sql = new Text2Sql();

  /** LLM 语义缓存配置 */
  private Cache cache = new Cache();

  /** Prompt 模板配置 */
  private PromptTemplate promptTemplate = new PromptTemplate();

  /** 护栏配置 */
  private Guardrail guardrail = new Guardrail();

  /** 工具调用配置 */
  private Tool tool = new Tool();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getDefaultSystemPrompt() {
    return defaultSystemPrompt;
  }

  public void setDefaultSystemPrompt(String defaultSystemPrompt) {
    this.defaultSystemPrompt = defaultSystemPrompt;
  }

  public Llm getLlm() {
    return llm;
  }

  public void setLlm(Llm llm) {
    this.llm = llm;
  }

  public Memory getMemory() {
    return memory;
  }

  public void setMemory(Memory memory) {
    this.memory = memory;
  }

  public Rag getRag() {
    return rag;
  }

  public void setRag(Rag rag) {
    this.rag = rag;
  }

  public Mcp getMcp() {
    return mcp;
  }

  public void setMcp(Mcp mcp) {
    this.mcp = mcp;
  }

  public Text2Sql getText2sql() {
    return text2sql;
  }

  public void setText2sql(Text2Sql text2sql) {
    this.text2sql = text2sql;
  }

  public Cache getCache() {
    return cache;
  }

  public void setCache(Cache cache) {
    this.cache = cache;
  }

  public PromptTemplate getPromptTemplate() {
    return promptTemplate;
  }

  public void setPromptTemplate(PromptTemplate promptTemplate) {
    this.promptTemplate = promptTemplate;
  }

  public Guardrail getGuardrail() {
    return guardrail;
  }

  public void setGuardrail(Guardrail guardrail) {
    this.guardrail = guardrail;
  }

  public Tool getTool() {
    return tool;
  }

  public void setTool(Tool tool) {
    this.tool = tool;
  }

  /** LLM 相关配置组（默认 Provider、模型、密钥、价格等）。 */
  public static class Llm {

    /** 默认温度 */
    private static final double DEFAULT_TEMPERATURE = 0.7;

    /** 默认最大 Token */
    private static final int DEFAULT_MAX_TOKENS = 2048;

    /** 默认调用超时（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /** 默认 Provider */
    private String defaultProvider = "default";

    /** 默认模型名称 */
    private String defaultModel = "default-model";

    /** API Key */
    private String apiKey = "";

    /** API Base URL */
    private String baseUrl = "";

    /** 默认温度 */
    private double temperature = DEFAULT_TEMPERATURE;

    /** 默认最大 Token */
    private int maxTokens = DEFAULT_MAX_TOKENS;

    /** 调用超时（秒） */
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    /** 多 Provider 配置 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>(16);