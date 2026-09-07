package com.njydsz.agent.domain.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 配置属性
 *
 * <p>包含 LLM、记忆、RAG、MCP、Text2SQL、缓存、Prompt 模板、护栏、工具等全量子系统的配置定义。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentProperties {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** L1 本地缓存默认最大条目数 */
  private static final int DEFAULT_L1_MAX_SIZE = 200;

  /** L1 本地缓存默认写入后过期时间（分钟） */
  private static final int DEFAULT_L1_EXPIRE_MINUTES = 5;


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

  /** 配额配置 */
  private Quota quota = new Quota();

  /** 记忆整合配置 */
  private MemoryConsolidation memoryConsolidation = new MemoryConsolidation();

  // ========================= LLM 配置 =========================

  /** LLM 相关配置组（默认 Provider、模型、密钥、价格等）。 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
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

    /** 模型单价映射（模型名 -> USD/千 Token） */
    private Map<String, Double> modelPrices = new LinkedHashMap<>(COLLECTION_CAPACITY);

    /** 未知模型兜底单价（USD/千 Token），未配置的模型使用此价格 */
    private BigDecimal fallbackPrice = new BigDecimal("0.001");

    /** 多 Provider 配置 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>(COLLECTION_CAPACITY);
  }

  /** 单个 Provider 配置（名称、模型、API 密钥、Base URL 等）。 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ProviderConfig {
    /** Provider 名称 */
    private String name;

    /** Provider 类型 (openai / deepseek 等) */
    private String type;

    /** 默认模型名称 */
    private String model;

    /** API Key */
    private String apiKey;

    /** API Base URL */
    private String baseUrl;

    /** 温度 */
    private Double temperature;

    /** 最大 Token */
    private Integer maxTokens;

    /** 调用超时（秒） */
    private Integer timeoutSeconds;

    /** 是否启用 */
    private boolean enabled = true;
  }

  // ========================= 记忆配置 =========================

  /** 记忆配置项 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Memory {

    /** Redis 过期默认时间（小时） */
    private static final int DEFAULT_TTL_HOURS = 24;

    /** 摘要压缩默认阈值（消息数） */
    private static final int DEFAULT_SUMMARY_THRESHOLD = 20;

    /** 摘要压缩默认保留的最近消息数 */
    private static final int DEFAULT_SUMMARY_KEEP_RECENT = 5;

    /** 是否启用记忆 */
    private boolean enabled = true;

    /** Token 字符比例估算系数（中英混合） */
    private BigDecimal tokenCharRatio = new BigDecimal("2.5");

    /** 最大保留消息数 */
    private int maxMessages = 10;

    /** 记忆类型: in-memory / redis / database */
    private String type = "in-memory";

    /** Redis 过期时间（小时） */
    private int ttlHours = DEFAULT_TTL_HOURS;

    /** 是否启用摘要压缩 */
    private boolean summaryEnabled = false;

    /** 摘要压缩阈值（消息数达到该值时触发摘要） */
    private int summaryThreshold = DEFAULT_SUMMARY_THRESHOLD;

    /** 摘要压缩时保留的最近消息数 */
    private int summaryKeepRecent = DEFAULT_SUMMARY_KEEP_RECENT;
  }

  // ========================= RAG 配置 =========================

  /** RAG 检索增强配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Rag {

    /** 默认 Top-K 召回数量 */
    private static final int DEFAULT_TOP_K = 5;

    /** 默认最小相似度阈值 */
    private static final double DEFAULT_MIN_SCORE = 0.7;

    /** 默认上下文 Token 预算 */
    private static final int DEFAULT_CONTEXT_TOKEN_BUDGET = 4096;

    /** 默认 Embedding 向量维度 */
    private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;

    /** 默认文本分块重叠字符数 */
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    /** 是否启用 RAG */
    private boolean enabled = true;

    /** 默认 Top-K 召回数量 */
    private int defaultTopK = DEFAULT_TOP_K;

    /** 默认最小相似度阈值 */
    private double defaultMinScore = DEFAULT_MIN_SCORE;

    /** 上下文 Token 预算 */
    private int contextTokenBudget = DEFAULT_CONTEXT_TOKEN_BUDGET;

    /** 向量数据库类型: in-memory / pgvector */
    private String vectorStore = "in-memory";

    /** Embedding 模型 */
    private String embeddingModel;

    /** Embedding API Key */
    private String embeddingApiKey = "";

    /** Embedding API Base URL */
    private String embeddingBaseUrl = "";

    /** Embedding 向量维度 */
    private int dimension = DEFAULT_EMBEDDING_DIMENSION;

    /** 文本分块大小 */
    private int chunkSize = 1000;

    /** 文本分块重叠字符数 */
    private int chunkOverlap = DEFAULT_CHUNK_OVERLAP;

    /** 是否启用租户隔离 */
    private boolean tenantIsolation = false;
  }

  // ========================= MCP 配置 =========================

  /** MCP 全局配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Mcp {
    /** 是否启用 MCP */
    private boolean enabled = true;
    /** MCP Server 列表 */
    private List<ServerInfo> servers;
    /** 默认超时时间（毫秒） */
    private Integer defaultTimeout;
  }

  /** MCP Server 连接配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ServerInfo {
    /** 服务器名称 */
    private String name;
    /** 传输类型：sse / streamable-http / stdio */
    private String transportType;
    /** 服务器 URL */
    private String url;
    /** 超时时间（毫秒） */
    private Integer timeout;
    /** 是否启用（默认 true） */
    private boolean enabled = true;
  }

  // ========================= Text2SQL 配置 =========================

  /** Text2SQL 自然语言转 SQL 配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Text2Sql {

    /** 默认增强链路最大召回表数量 */
    private static final int DEFAULT_SCHEMA_RECALL_MAX_TABLES = 5;

    /** 默认一致性分数阈值 */
    private static final double DEFAULT_CONSISTENCY_THRESHOLD = 0.7;

    /** 是否启用 Text2SQL */
    private boolean enabled = true;

    /** 最大返回行数 */
    private int maxRows = 100;

    /** 查询超时（毫秒） */
    private int queryTimeoutMs = 10000;

    /** 数据库 JDBC URL */
    private String jdbcUrl;

    /** 数据库用户名 */
    private String username;

    /** 数据库密码 */
    private String password;

    /** 是否启用增强链路（Schema 召回 + 可行性评估 + 语义一致性） */
    private boolean enhanced = false;

    /** 是否启用 Schema 智能召回 */
    private boolean schemaRecallEnabled = true;

    /** 最大召回表数量 */
    private int schemaRecallMaxTables = DEFAULT_SCHEMA_RECALL_MAX_TABLES;

    /** 是否启用可行性评估 */
    private boolean feasibilityCheckEnabled = true;

    /** 是否启用语义一致性校验 */
    private boolean consistencyCheckEnabled = true;

    /** 一致性分数阈值，低于此值拒绝 SQL */
    private double consistencyThreshold = DEFAULT_CONSISTENCY_THRESHOLD;
  }

  // ========================= 缓存配置 =========================

  /** LLM 语义缓存配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Cache {

    /** 默认缓存 TTL（分钟） */
    private static final int DEFAULT_TTL_MINUTES = 60;

    /** 默认缓存相似度阈值 */
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.95;

    /** 是否启用语义缓存 */
    private boolean enabled = false;

    /** 缓存 TTL（分钟） */
    private int ttlMinutes = DEFAULT_TTL_MINUTES;

    /** 最大缓存条目数 */
    private int maxSize = 1000;

    /** 缓存相似度阈值 */
    private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;

    /** 缓存类型: caffeine / redis */
    private String type = "caffeine";

    /** L1 本地缓存最大条目数 */
    private int l1MaxSize = DEFAULT_L1_MAX_SIZE;

    /** L1 本地缓存写入后过期时间（分钟） */
    private int l1ExpireMinutes = DEFAULT_L1_EXPIRE_MINUTES;
  }

  // ========================= Prompt 模板配置 =========================

  /** Prompt 模板配置（模板编码 -> 外部模板系统的模板 code 映射） */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PromptTemplate {
    /** 是否启用 Prompt 模板 */
    private boolean enabled = true;

    /** 默认系统 Prompt 模板编码 */
    private String defaultSystemCode = "DEFAULT_SYSTEM";

    /** ReAct 系统 Prompt 模板编码 */
    private String reactSystemCode = "REACT_SYSTEM";

    /** Plan-Execute 规划模板编码 */
    private String planExecutePlanCode = "PLAN_EXECUTE_PLAN";

    /** Plan-Execute 规划系统 Prompt 模板编码 */
    private String planExecutePlanSystemCode = "PLAN_EXECUTE_PLAN_SYSTEM";

    /** Plan-Execute 重规划模板编码 */
    private String planExecuteReplanCode = "PLAN_EXECUTE_REPLAN";

    /** Supervisor 规划模板编码 */
    private String supervisorPlanCode = "SUPERVISOR_PLAN";

    /** Supervisor 规划系统 Prompt 模板编码 */
    private String supervisorPlanSystemCode = "SUPERVISOR_PLAN_SYSTEM";
  }

  // ========================= 护栏配置 =========================

  /** 护栏配置（输入/输出安全控制） */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Guardrail {

    /** 默认每分钟最大请求数 */
    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 60;

    /** 是否启用护栏 */
    private boolean enabled = true;

    /** 是否启用输入护栏（Prompt 注入检测） */
    private boolean inputGuardrailEnabled = true;

    /** 是否启用输出护栏（内容审核） */
    private boolean outputGuardrailEnabled = true;

    /** PII 脱敏启用 */
    private boolean piiMaskingEnabled = true;

    /** 每分钟最大请求数 */
    private int maxRequestsPerMinute = DEFAULT_MAX_REQUESTS_PER_MINUTE;

    /** 拒绝时的提示消息 */
    private String rejectionMessage = "请求被安全护栏拒绝";
  }

  // ========================= 工具配置 =========================

  /** 工具调用配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Tool {

    /** 默认单次工具调用超时（毫秒） */
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    /** 默认单次工具调用超时（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** 默认最大工具调用深度 */
    private static final int DEFAULT_MAX_DEPTH = 5;

    /** 是否启用工具调用 */
    private boolean enabled = true;

    /** 单次工具调用超时（毫秒） */
    private int timeoutMs = DEFAULT_TIMEOUT_MS;

    /** 单次工具调用超时（秒） */
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    /** 最大工具调用深度 */
    private int maxDepth = DEFAULT_MAX_DEPTH;

    /** 是否启用并行工具调用 */
    private boolean parallelEnabled = false;

    /** 工具执行失败时是否快速失败 */
    private boolean failFast = true;
  }

  // ========================= 配额配置 =========================

  /** 配额与成本控制配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Quota {

    /** 默认每日 Token 限额 */
    private static final long DEFAULT_DAILY_TOKEN_LIMIT = 1000000L;

    /** 默认告警阈值（0.0-1.0，达到配额的百分比时告警） */
    private static final double DEFAULT_ALERT_THRESHOLD = 0.8;

    /** 是否启用配额控制 */
    private boolean enabled = true;

    /** 每日 Token 限额 */
    private long dailyTokenLimit = DEFAULT_DAILY_TOKEN_LIMIT;

    /** 每月预算（USD） */
    private double monthlyBudgetUsd = 100.0;

    /** 告警阈值（0.0-1.0，达到配额的百分比时告警） */
    private double alertThreshold = DEFAULT_ALERT_THRESHOLD;
  }

  // ========================= 记忆整合配置 =========================

  /** 记忆整合（Dreaming）配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MemoryConsolidation {

    /** 默认每批处理对话数 */
    private static final int DEFAULT_BATCH_SIZE = 50;

    /** 是否启用记忆整合 */
    private boolean enabled = false;

    /** 是否启用 Dreaming 定时整合 */
    private boolean dreamingEnabled = false;

    /** 每批处理对话数 */
    private int batchSize = DEFAULT_BATCH_SIZE;

    /** Cron 表达式 */
    private String cron = "0 30 2 * * ?";
  }
}
