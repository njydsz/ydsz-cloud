package com.njydsz.agent.server.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.agent.domain.gateway.PromptTemplateProvider;

/**
 * Agent 模块配置属性
 *
 * @author ydsz-team
 * @since 1.0.0
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
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /** 模型价格配置（key = 模型名前缀，value = 每千 token 价格 USD） */
    private Map<String, Double> modelPrices = new LinkedHashMap<>();

    public String getDefaultProvider() {
      return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
      this.defaultProvider = defaultProvider;
    }

    public String getDefaultModel() {
      return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
      this.defaultModel = defaultModel;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public double getTemperature() {
      return temperature;
    }

    public void setTemperature(double temperature) {
      this.temperature = temperature;
    }

    public int getMaxTokens() {
      return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
      this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }

    public Map<String, ProviderConfig> getProviders() {
      return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
      this.providers = providers;
    }

    public Map<String, Double> getModelPrices() {
      return modelPrices;
    }

    public void setModelPrices(Map<String, Double> modelPrices) {
      this.modelPrices = modelPrices;
    }
  }

  /** Provider 配置（多模型供应商） */
  public static class ProviderConfig {
    /** Provider 名称 */
    private String name;

    /** API Key */
    private String apiKey = "";

    /** API Base URL */
    private String baseUrl = "";

    /** 支持的模型列表 */
    private List<String> models = new ArrayList<>();

    /** 是否启用 */
    private boolean enabled = true;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public List<String> getModels() {
      return models;
    }

    public void setModels(List<String> models) {
      this.models = models;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /** 对话记忆相关配置组（TTL 与上下文窗口大小）。 */
  public static class Memory {

    /** 默认对话记忆 TTL（小时） */
    private static final int DEFAULT_TTL_HOURS = 24;

    /** 默认滑动窗口最大消息数 */
    private static final int DEFAULT_MAX_MESSAGES = 20;

    /** 默认触发摘要压缩的消息条数阈值 */
    private static final int DEFAULT_SUMMARY_THRESHOLD = 20;

    /** 默认上下文 Token 预算（估算值） */
    private static final int DEFAULT_TOKEN_BUDGET = 4000;

    /** 默认 Token 估算的字符系数（Char/Token） */
    private static final double DEFAULT_TOKEN_CHAR_RATIO = 2.5;

    /** 对话记忆 TTL（小时） */
    private int ttlHours = DEFAULT_TTL_HOURS;

    /** 滑动窗口最大消息数 */
    private int maxMessages = DEFAULT_MAX_MESSAGES;

    /** 是否启用摘要压缩记忆（长对话自动压缩为摘要，避免上下文膨胀） */
    private boolean summaryEnabled = false;

    /** 触发摘要压缩的消息条数阈值 */
    private int summaryThreshold = DEFAULT_SUMMARY_THRESHOLD;

    /** 压缩后保留的最近原始消息条数 */
    private int summaryKeepRecent = 10;

    /**
     * 上下文 Token 预算（估算值）。
     *
     * <p>当对话历史估算 Token 数超过此预算时，触发摘要压缩。 基于字符数估算（中文约 1.5 Char/Token，英文约 4 Char/Token），
     * 默认 4000 Token 适合大多数 LLM 的上下文窗口。
     */
    private int tokenBudget = DEFAULT_TOKEN_BUDGET;

    /**
     * Token 估算的字符系数（Char/Token）。
     *
     * <p>中文为主场景取 1.5，英文为主取 4.0，中英混合取 2.5。 用于将字符数转换为估算 Token 数。
     */
    private double tokenCharRatio = DEFAULT_TOKEN_CHAR_RATIO;

    public int getTtlHours() {
      return ttlHours;
    }

    public void setTtlHours(int ttlHours) {
      this.ttlHours = ttlHours;
    }

    public int getMaxMessages() {
      return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
      this.maxMessages = maxMessages;
    }

    public boolean isSummaryEnabled() {
      return summaryEnabled;
    }

    public void setSummaryEnabled(boolean summaryEnabled) {
      this.summaryEnabled = summaryEnabled;
    }

    public int getSummaryThreshold() {
      return summaryThreshold;
    }

    public void setSummaryThreshold(int summaryThreshold) {
      this.summaryThreshold = summaryThreshold;
    }

    public int getSummaryKeepRecent() {
      return summaryKeepRecent;
    }

    public void setSummaryKeepRecent(int summaryKeepRecent) {
      this.summaryKeepRecent = summaryKeepRecent;
    }

    public int getTokenBudget() {
      return tokenBudget;
    }

    public void setTokenBudget(int tokenBudget) {
      this.tokenBudget = tokenBudget;
    }

    public double getTokenCharRatio() {
      return tokenCharRatio;
    }

    public void setTokenCharRatio(double tokenCharRatio) {
      this.tokenCharRatio = tokenCharRatio;
    }
  }

  /** 记忆整合配置组（Dreaming 机制、实时提取）。 */
  private MemoryConsolidation memoryConsolidation = new MemoryConsolidation();

  public MemoryConsolidation getMemoryConsolidation() {
    return memoryConsolidation;
  }

  public void setMemoryConsolidation(MemoryConsolidation memoryConsolidation) {
    this.memoryConsolidation = memoryConsolidation;
  }

  /** RAG 检索相关配置组（开关、向量存储类型、Embedding 模型等）。 */
  public static class Rag {

    /** 默认向量维度 */
    private static final int DEFAULT_DIMENSION = 1536;

    /** 默认分块大小（字符数） */
    private static final int DEFAULT_CHUNK_SIZE = 500;

    /** 默认分块重叠（字符数） */
    private static final int DEFAULT_CHUNK_OVERLAP = 50;

    /** 默认检索 Top-K */
    private static final int DEFAULT_TOP_K = 5;

    /** 默认最小相似度阈值 */
    private static final double DEFAULT_MIN_SCORE = 0.7;

    /** 默认上下文 Token 预算（估算值） */
    private static final int DEFAULT_CONTEXT_TOKEN_BUDGET = 3000;

    /** 是否启用 RAG */
    private boolean enabled = false;

    /** 向量存储类型（pgvector / memory） */
    private String vectorStore = "memory";

    /** Embedding 模型 */
    private String embeddingModel = "text-embedding-3-small";

    /** Embedding API Key（默认复用 LLM API Key） */
    private String embeddingApiKey = "";

    /** Embedding API Base URL（默认复用 LLM Base URL） */
    private String embeddingBaseUrl = "";

    /** 向量维度 */
    private int dimension = DEFAULT_DIMENSION;

    /** 分块大小 */
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    /** 分块重叠 */
    private int chunkOverlap = DEFAULT_CHUNK_OVERLAP;

    /** 检索 Top-K */
    private int topK = DEFAULT_TOP_K;

    /** 最小相似度阈值 */
    private double minScore = DEFAULT_MIN_SCORE;

    /**
     * 上下文 Token 预算（估算值）。
     *
     * <p>RAG 检索结果拼接为上下文时，总 Token 不超过此预算。 默认 3000 Token（约占 GPT-4o 上下文窗口的 25%），
     * 避免检索结果占用过多上下文导致 LLM 回复质量下降。
     */
    private int contextTokenBudget = DEFAULT_CONTEXT_TOKEN_BUDGET;

    /**
     * 是否启用 Reranker 精排（对召回结果做重排序，提升 Top-K 精确度）。
     *
     * <p>需配合实现 domain 层 {@link com.njydsz.agent.domain.rag.Reranker} 接口的 Bean（如 Cross-Encoder 模型）。
     * 未配置 Reranker Bean 时默认使用 IdentityReranker（恒等排序，仅截断）。
     */
    private boolean rerankerEnabled = false;

    /** 是否启用 RAG 多租户隔离（启用时向量/全文检索 SQL 显式追加 tenant_id 条件） */
    private boolean tenantIsolation = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getVectorStore() {
      return vectorStore;
    }

    public void setVectorStore(String vectorStore) {
      this.vectorStore = vectorStore;
    }

    public String getEmbeddingModel() {
      return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
      this.embeddingModel = embeddingModel;
    }

    public String getEmbeddingApiKey() {
      return embeddingApiKey;
    }

    public void setEmbeddingApiKey(String embeddingApiKey) {
      this.embeddingApiKey = embeddingApiKey;
    }

    public String getEmbeddingBaseUrl() {
      return embeddingBaseUrl;
    }

    public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
      this.embeddingBaseUrl = embeddingBaseUrl;
    }

    public int getDimension() {
      return dimension;
    }

    public void setDimension(int dimension) {
      this.dimension = dimension;
    }

    public int getChunkSize() {
      return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
      this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
      return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
      this.chunkOverlap = chunkOverlap;
    }

    public int getTopK() {
      return topK;
    }

    public void setTopK(int topK) {
      this.topK = topK;
    }

    public double getMinScore() {
      return minScore;
    }

    public void setMinScore(double minScore) {
      this.minScore = minScore;
    }

    public int getContextTokenBudget() {
      return contextTokenBudget;
    }

    public void setContextTokenBudget(int contextTokenBudget) {
      this.contextTokenBudget = contextTokenBudget;
    }

    public boolean isRerankerEnabled() {
      return rerankerEnabled;
    }

    public void setRerankerEnabled(boolean rerankerEnabled) {
      this.rerankerEnabled = rerankerEnabled;
    }

    public boolean isTenantIsolation() {
      return tenantIsolation;
    }

    public void setTenantIsolation(boolean tenantIsolation) {
      this.tenantIsolation = tenantIsolation;
    }
  }

  /**
   * MCP（Model Context Protocol）配置组
   *
   * <p>支持配置多个 MCP Server，自动发现并注册其工具到 {@link com.njydsz.agent.domain.tool.ToolRegistry}。
   */
  public static class Mcp {
    /** 是否启用 MCP */
    private boolean enabled = false;

    /** MCP Server 列表 */
    private List<ServerInfo> servers = new ArrayList<>();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public List<ServerInfo> getServers() {
      return servers;
    }

    public void setServers(List<ServerInfo> servers) {
      this.servers = servers;
    }
  }

  /**
   * LLM 语义缓存配置组
   *
   * <p>基于 Redis 缓存 LLM 响应，对 deterministic (temperature=0) 请求生效。
   */
  public static class Cache {

    /** 默认缓存 TTL（分钟） */
    private static final int DEFAULT_TTL_MINUTES = 60;

    /** 默认最大缓存条目数 */
    private static final int DEFAULT_MAX_SIZE = 500;

    /** 是否启用语义缓存 */
    private boolean enabled = false;

    /** 缓存 TTL（分钟） */
    private int ttlMinutes = DEFAULT_TTL_MINUTES;

    /** 最大缓存条目数 */
    private int maxSize = DEFAULT_MAX_SIZE;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getTtlMinutes() {
      return ttlMinutes;
    }

    public void setTtlMinutes(int ttlMinutes) {
      this.ttlMinutes = ttlMinutes;
    }

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(int maxSize) {
      this.maxSize = maxSize;
    }
  }

  /**
   * Text2SQL 自然语言查询配置组
   *
   * <p>将用户的自然语言查询转换为 SQL 并执行，返回结构化数据。 包含多重安全护栏（仅 SELECT、SQL 注入检测、结果行数限制）。
   */
  public static class Text2Sql {
    /** 是否启用 Text2SQL */
    private boolean enabled = false;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /**
   * Prompt 模板配置组
   *
   * <p>配置默认使用的 Prompt 模板编码，运行时由 {@link PromptTemplateProvider} 从数据库加载。
   */
  public static class PromptTemplate {
    /** 默认系统 Prompt 模板编码 */
    private String defaultSystemCode = "DEFAULT_SYSTEM";

    /** ReAct 模式 Prompt 模板编码 */
    private String reactSystemCode = "REACT_SYSTEM";

    /** Plan-Execute 模式 Prompt 模板编码 */
    private String planSystemCode = "PLAN_SYSTEM";

    /** Plan-Execute 规划阶段 Prompt 模板编码（分解用户需求的指令） */
    private String planExecutePlanCode = "PLAN_EXECUTE_PLAN";

    /** Plan-Execute 规划阶段系统 Prompt 编码 */
    private String planExecutePlanSystemCode = "PLAN_EXECUTE_PLAN_SYSTEM";

    /** Plan-Execute 重规划阶段 Prompt 模板编码 */
    private String planExecuteReplanCode = "PLAN_EXECUTE_REPLAN";

    /** Supervisor 任务分解 Prompt 模板编码 */
    private String supervisorPlanCode = "SUPERVISOR_PLAN";

    /** Supervisor 规划阶段系统 Prompt 编码 */
    private String supervisorPlanSystemCode = "SUPERVISOR_PLAN_SYSTEM";

    public String getDefaultSystemCode() {
      return defaultSystemCode;
    }

    public void setDefaultSystemCode(String defaultSystemCode) {
      this.defaultSystemCode = defaultSystemCode;
    }

    public String getReactSystemCode() {
      return reactSystemCode;
    }

    public void setReactSystemCode(String reactSystemCode) {
      this.reactSystemCode = reactSystemCode;
    }

    public String getPlanSystemCode() {
      return planSystemCode;
    }

    public void setPlanSystemCode(String planSystemCode) {
      this.planSystemCode = planSystemCode;
    }

    public String getPlanExecutePlanCode() {
      return planExecutePlanCode;
    }

    public void setPlanExecutePlanCode(String planExecutePlanCode) {
      this.planExecutePlanCode = planExecutePlanCode;
    }

    public String getPlanExecutePlanSystemCode() {
      return planExecutePlanSystemCode;
    }

    public void setPlanExecutePlanSystemCode(String planExecutePlanSystemCode) {
      this.planExecutePlanSystemCode = planExecutePlanSystemCode;
    }

    public String getPlanExecuteReplanCode() {
      return planExecuteReplanCode;
    }

    public void setPlanExecuteReplanCode(String planExecuteReplanCode) {
      this.planExecuteReplanCode = planExecuteReplanCode;
    }

    public String getSupervisorPlanCode() {
      return supervisorPlanCode;
    }

    public void setSupervisorPlanCode(String supervisorPlanCode) {
      this.supervisorPlanCode = supervisorPlanCode;
    }

    public String getSupervisorPlanSystemCode() {
      return supervisorPlanSystemCode;
    }

    public void setSupervisorPlanSystemCode(String supervisorPlanSystemCode) {
      this.supervisorPlanSystemCode = supervisorPlanSystemCode;
    }
  }

  /** MCP Server 连接信息 */
  public static class ServerInfo {
    /** Server 名称（唯一标识） */
    private String name;

    /** 传输类型（sse / stdio） */
    private String transport = "sse";

    /** SSE 端点 URL（transport=sse 时必填） */
    private String url;

    /** 默认调用超时（秒） */
    private static final int DEFAULT_MCP_TIMEOUT_SECONDS = 30;

    /** 调用超时（秒） */
    private int timeoutSeconds = DEFAULT_MCP_TIMEOUT_SECONDS;

    /** 是否启用 */
    private boolean enabled = true;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getTransport() {
      return transport;
    }

    public void setTransport(String transport) {
      this.transport = transport;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /**
   * 护栏配置组
   *
   * <p>P1-3 重构：PromptInjection 降级为可选护栏，默认关闭，需显式开启。 核心护栏（AgentRequestGuard 幂等 + 限流、PiiMaskingGuardrail 输出脱敏）不受影响。
   */
  public static class Guardrail {
    /**
     * 是否启用 Prompt 注入检测护栏。
     *
     * <p>默认关闭。Prompt 注入检测基于正则模式匹配，存在较高误杀率且易被绕过， 在 RateLimit + Audit 已覆盖核心安全需求的场景下价值有限。
     * 如业务确需注入检测（如面向公网 C 端场景），可显式开启：{@code ydsz.agent.guardrail.promptInjectionEnabled=true}。
     */
    private boolean promptInjectionEnabled = false;

    /**
     * 单用户每分钟请求上限（AgentRequestGuard 限流阈值，P2 修复：原值硬编码不可配置）。
     */
    private int maxRequestsPerMinute = 10;

    /**
     * 输出护栏拒绝时的兜底文案（P2 修复：原文案硬编码不可配置）。
     */
    private String rejectionMessage = "抱歉，我无法回答这个问题。";

    public boolean isPromptInjectionEnabled() {
      return promptInjectionEnabled;
    }

    public void setPromptInjectionEnabled(boolean promptInjectionEnabled) {
      this.promptInjectionEnabled = promptInjectionEnabled;
    }

    public int getMaxRequestsPerMinute() {
      return maxRequestsPerMinute;
    }

    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
      this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public String getRejectionMessage() {
      return rejectionMessage;
    }

    public void setRejectionMessage(String rejectionMessage) {
      this.rejectionMessage = rejectionMessage;
    }
  }

  /**
   * 工具调用配置组
   *
   * <p>控制工具执行的超时、错误处理等行为。
   */
  public static class Tool {

    /** 默认工具执行超时（秒） */
    private static final int DEFAULT_TOOL_TIMEOUT_SECONDS = 30;

    /**
     * 工具执行超时（秒）。
     *
     * <p>工具执行超过此时间未完成将被中断，防止单个工具挂起阻塞整个 Agent 迭代循环。
     */
    private int timeoutSeconds = DEFAULT_TOOL_TIMEOUT_SECONDS;

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }
  }

  /** 租户配额配置组 */
  private Quota quota = new Quota();

  /** 运行时管理配置 */
  private Runtime runtime = new Runtime();

  /** 触发器配置 */
  private Trigger trigger = new Trigger();

  public Quota getQuota() {
    return quota;
  }

  public void setQuota(Quota quota) {
    this.quota = quota;
  }

  public Runtime getRuntime() {
    return runtime;
  }

  public void setRuntime(Runtime runtime) {
    this.runtime = runtime;
  }

  public Trigger getTrigger() {
    return trigger;
  }

  public void setTrigger(Trigger trigger) {
    this.trigger = trigger;
  }

  /**
   * 租户 LLM 配额配置
   *
   * <p>控制单租户在 LLM 调用层面的用量上限，包含每日 Token 限额和月度预算限额。 配额为 0 表示不限制该维度。
   */
  public static class Quota {
    /** 是否启用配额校验 */
    private boolean enabled = true;

    /** 每日 Token 限额（0 = 不限制） */
    private long dailyTokenLimit = 1000000L;

    /** 月度预算（USD，0 = 不限制） */
    private double monthlyBudgetUsd = 1000.0;

    /** 告警阈值（0.0-1.0，默认 0.8 即 80%） */
    private double alertThreshold = 0.8;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getDailyTokenLimit() {
      return dailyTokenLimit;
    }

    public void setDailyTokenLimit(long dailyTokenLimit) {
      this.dailyTokenLimit = dailyTokenLimit;
    }

    public double getMonthlyBudgetUsd() {
      return monthlyBudgetUsd;
    }

    public void setMonthlyBudgetUsd(double monthlyBudgetUsd) {
      this.monthlyBudgetUsd = monthlyBudgetUsd;
    }

    public double getAlertThreshold() {
      return alertThreshold;
    }

    public void setAlertThreshold(double alertThreshold) {
      this.alertThreshold = alertThreshold;
    }
  }

  /**
   * 运行时管理配置组。
   *
   * <p>控制 Agent 运行时管理面板的行为，包括会话清理间隔、最大会话保留时长等。
   */
  public static class Runtime {
    /** 是否启用运行时管理功能（会话追踪、强制回收） */
    private boolean enabled = true;

    /** 会话清理任务执行间隔（毫秒），默认 10 分钟 */
    private long cleanupIntervalMs = 600000L;

    /** 非活跃会话最大保留时长（分钟），默认 120 分钟（2 小时） */
    private int staleThresholdMinutes = 120;

    /** 单次查询最大返回会话数，默认 200 */
    private int maxQueryLimit = 200;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getCleanupIntervalMs() {
      return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(long cleanupIntervalMs) {
      this.cleanupIntervalMs = cleanupIntervalMs;
    }

    public int getStaleThresholdMinutes() {
      return staleThresholdMinutes;
    }

    public void setStaleThresholdMinutes(int staleThresholdMinutes) {
      this.staleThresholdMinutes = staleThresholdMinutes;
    }

    public int getMaxQueryLimit() {
      return maxQueryLimit;
    }

    public void setMaxQueryLimit(int maxQueryLimit) {
      this.maxQueryLimit = maxQueryLimit;
    }
  }

  /**
   * 记忆整合（Dreaming）配置组。
   *
   * <p>控制对话后记忆提取和定时 Dreaming 任务的行为。
   */
  public static class MemoryConsolidation {
    /** 是否启用记忆整合功能 */
    private boolean enabled = false;

    /** 是否启用对话后实时记忆提取 */
    private boolean realtimeEnabled = true;

    /** 是否启用定时 Dreaming 任务 */
    private boolean dreamingEnabled = false;

    /** Dreaming 任务 Cron 表达式，默认每天凌晨 2:30 */
    private String dreamingCron = "0 30 2 * * ?";

    /** 触发记忆提取的最小消息数 */
    private int minMessages = 4;

    /** 单次批量处理最大对话数 */
    private int batchSize = 50;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isRealtimeEnabled() {
      return realtimeEnabled;
    }

    public void setRealtimeEnabled(boolean realtimeEnabled) {
      this.realtimeEnabled = realtimeEnabled;
    }

    public boolean isDreamingEnabled() {
      return dreamingEnabled;
    }

    public void setDreamingEnabled(boolean dreamingEnabled) {
      this.dreamingEnabled = dreamingEnabled;
    }

    public String getDreamingCron() {
      return dreamingCron;
    }

    public void setDreamingCron(String dreamingCron) {
      this.dreamingCron = dreamingCron;
    }

    public int getMinMessages() {
      return minMessages;
    }

    public void setMinMessages(int minMessages) {
      this.minMessages = minMessages;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }
  }

  /**
   * 触发器配置组。
   *
   * <p>控制事件驱动触发器的行为，包括启用状态、扫描间隔、租户限额等。
   */
  public static class Trigger {
    /** 是否启用触发器功能 */
    private boolean enabled = true;

    /** 定时触发器扫描间隔（毫秒），默认 60 秒 */
    private long scanIntervalMs = 60000L;

    /** 触发器清理任务执行间隔（毫秒），默认 10 分钟 */
    private long cleanupIntervalMs = 600000L;

    /** 单租户最大触发器数量 */
    private int maxTriggersPerTenant = 50;

    /** 默认每小时最大执行次数 */
    private int defaultMaxExecutionsPerHour = 60;

    /** 去重窗口（分钟） */
    private int deduplicationWindowMinutes = 5;

    /** 最大递归深度（防止触发器链无限循环） */
    private int maxRecursionDepth = 3;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getScanIntervalMs() {
      return scanIntervalMs;
    }

    public void setScanIntervalMs(long scanIntervalMs) {
      this.scanIntervalMs = scanIntervalMs;
    }

    public long getCleanupIntervalMs() {
      return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(long cleanupIntervalMs) {
      this.cleanupIntervalMs = cleanupIntervalMs;
    }

    public int getMaxTriggersPerTenant() {
      return maxTriggersPerTenant;
    }

    public void setMaxTriggersPerTenant(int maxTriggersPerTenant) {
      this.maxTriggersPerTenant = maxTriggersPerTenant;
    }

    public int getDefaultMaxExecutionsPerHour() {
      return defaultMaxExecutionsPerHour;
    }

    public void setDefaultMaxExecutionsPerHour(int defaultMaxExecutionsPerHour) {
      this.defaultMaxExecutionsPerHour = defaultMaxExecutionsPerHour;
    }

    public int getDeduplicationWindowMinutes() {
      return deduplicationWindowMinutes;
    }

    public void setDeduplicationWindowMinutes(int deduplicationWindowMinutes) {
      this.deduplicationWindowMinutes = deduplicationWindowMinutes;
    }

    public int getMaxRecursionDepth() {
      return maxRecursionDepth;
    }

    public void setMaxRecursionDepth(int maxRecursionDepth) {
      this.maxRecursionDepth = maxRecursionDepth;
    }
  }
}
