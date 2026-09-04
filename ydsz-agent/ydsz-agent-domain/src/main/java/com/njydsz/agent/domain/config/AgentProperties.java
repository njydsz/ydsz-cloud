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

    /** 默认 Provider */
    private String defaultProvider = "default";

    /** 默认模型名称 */
    private String defaultModel = "default-model";

    /** API Key */
    private String apiKey = "";

    /** API Base URL */
    private String baseUrl = "";

    /** 默认温度 */
    private double temperature = 0.7;

    /** 默认最大 Token */
    private int maxTokens = 2048;

    /** 调用超时（秒） */
    private int timeoutSeconds = 60;

    /** 模型单价映射（模型名 -> USD/千 Token） */
    private Map<String, Double> modelPrices = new LinkedHashMap<>(16);

    /** 多 Provider 配置 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>(16);
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
    /** 是否启用记忆 */
    private boolean enabled = true;

    /** Token 字符比例估算系数（中英混合） */
    private BigDecimal tokenCharRatio = new BigDecimal("2.5");

    /** 最大保留消息数 */
    private int maxMessages = 10;

    /** 记忆类型: in-memory / redis / database */
    private String type = "in-memory";

    /** Redis 过期时间（小时） */
    private int ttlHours = 24;

    /** 是否启用摘要压缩 */
    private boolean summaryEnabled = false;

    /** 摘要压缩阈值（消息数达到该值时触发摘要） */
    private int summaryThreshold = 20;

    /** 摘要压缩时保留的最近消息数 */
    private int summaryKeepRecent = 5;
  }

  // ========================= RAG 配置 =========================

  /** RAG 检索增强配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Rag {
    /** 是否启用 RAG */
    private boolean enabled = true;

    /** 默认 Top-K 召回数量 */
    private int defaultTopK = 5;

    /** 默认最小相似度阈值 */
    private double defaultMinScore = 0.7;

    /** 上下文 Token 预算 */
    private int contextTokenBudget = 4096;

    /** 向量数据库类型: in-memory / pgvector */
    private String vectorStore = "in-memory";

    /** Embedding 模型 */
    private String embeddingModel;

    /** Embedding API Key */
    private String embeddingApiKey = "";

    /** Embedding API Base URL */
    private String embeddingBaseUrl = "";

    /** Embedding 向量维度 */
    private int dimension = 1536;

    /** 文本分块大小 */
    private int chunkSize = 1000;

    /** 文本分块重叠字符数 */
    private int chunkOverlap = 200;

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
  }

  // ========================= 缓存配置 =========================

  /** LLM 语义缓存配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Cache {
    /** 是否启用语义缓存 */
    private boolean enabled = false;

    /** 缓存 TTL（分钟） */
    private int ttlMinutes = 60;

    /** 最大缓存条目数 */
    private int maxSize = 1000;

    /** 缓存相似度阈值 */
    private double similarityThreshold = 0.95;

    /** 缓存类型: caffeine / redis */
    private String type = "caffeine";
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
    /** 是否启用护栏 */
    private boolean enabled = true;

    /** 是否启用输入护栏（Prompt 注入检测） */
    private boolean inputGuardrailEnabled = true;

    /** 是否启用输出护栏（内容审核） */
    private boolean outputGuardrailEnabled = true;

    /** PII 脱敏启用 */
    private boolean piiMaskingEnabled = true;

    /** 每分钟最大请求数 */
    private int maxRequestsPerMinute = 60;

    /** 拒绝时的提示消息 */
    private String rejectionMessage = "请求被安全护栏拒绝";
  }

  // ========================= 工具配置 =========================

  /** 工具调用配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Tool {
    /** 是否启用工具调用 */
    private boolean enabled = true;

    /** 单次工具调用超时（毫秒） */
    private int timeoutMs = 30000;

    /** 单次工具调用超时（秒） */
    private int timeoutSeconds = 30;

    /** 最大工具调用深度 */
    private int maxDepth = 5;

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
    /** 是否启用配额控制 */
    private boolean enabled = true;

    /** 每日 Token 限额 */
    private long dailyTokenLimit = 1000000;

    /** 每月预算（USD） */
    private double monthlyBudgetUsd = 100.0;

    /** 告警阈值（0.0-1.0，达到配额的百分比时告警） */
    private double alertThreshold = 0.8;
  }

  // ========================= 记忆整合配置 =========================

  /** 记忆整合（Dreaming）配置 */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MemoryConsolidation {
    /** 是否启用记忆整合 */
    private boolean enabled = false;

    /** 是否启用 Dreaming 定时整合 */
    private boolean dreamingEnabled = false;

    /** 每批处理对话数 */
    private int batchSize = 50;

    /** Cron 表达式 */
    private String cron = "0 30 2 * * ?";
  }
}
