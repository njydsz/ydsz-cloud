package com.njydsz.agent.server.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
    private String defaultSystemPrompt = "你是 YDSZ 项目管理信息系统的智能助手。你可以帮助用户查询项目信息、分析项目进度、发起审批流程、发送消息通知等。请用中文回答。";

    /** LLM 配置 */
    private Llm llm = new Llm();

    /** 记忆配置 */
    private Memory memory = new Memory();

    /** RAG 配置 */
    private Rag rag = new Rag();

    /** MCP 配置 */
    private Mcp mcp = new Mcp();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDefaultSystemPrompt() { return defaultSystemPrompt; }
    public void setDefaultSystemPrompt(String defaultSystemPrompt) { this.defaultSystemPrompt = defaultSystemPrompt; }
    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }
    public Memory getMemory() { return memory; }
    public void setMemory(Memory memory) { this.memory = memory; }
    public Rag getRag() { return rag; }
    public void setRag(Rag rag) { this.rag = rag; }
    public Mcp getMcp() { return mcp; }
    public void setMcp(Mcp mcp) { this.mcp = mcp; }

    /**
     * LLM 相关配置组（默认 Provider、模型、密钥、价格等）。
     */
    public static class Llm {
        /** 默认 Provider（openai / deepseek / qwen / ollama） */
        private String defaultProvider = "openai";
        /** 默认模型名称 */
        private String defaultModel = "gpt-4o-mini";
        /** API Key */
        private String apiKey = "";
        /** API Base URL */
        private String baseUrl = "https://api.openai.com/v1";
        /** 默认温度 */
        private double temperature = 0.7;
        /** 默认最大 Token */
        private int maxTokens = 2048;
        /** 调用超时（秒） */
        private int timeoutSeconds = 60;
        /** 多 Provider 配置（key = provider 名称，如 openai/deepseek/qwen） */
        private Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        /** 模型价格配置（key = 模型名前缀，value = 每千 token 价格 USD） */
        private Map<String, Double> modelPrices = new LinkedHashMap<>();

        public String getDefaultProvider() { return defaultProvider; }
        public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }
        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public Map<String, ProviderConfig> getProviders() { return providers; }
        public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }
        public Map<String, Double> getModelPrices() { return modelPrices; }
        public void setModelPrices(Map<String, Double> modelPrices) { this.modelPrices = modelPrices; }
    }

    /**
     * Provider 配置（多模型供应商）
     */
    public static class ProviderConfig {
        /** Provider 名称（如 openai/deepseek/qwen/ollama） */
        private String name;
        /** API Key */
        private String apiKey = "";
        /** API Base URL */
        private String baseUrl = "https://api.openai.com/v1";
        /** 支持的模型列表 */
        private List<String> models = new ArrayList<>();
        /** 是否启用 */
        private boolean enabled = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public List<String> getModels() { return models; }
        public void setModels(List<String> models) { this.models = models; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * 对话记忆相关配置组（TTL 与上下文窗口大小）。
     */
    public static class Memory {
        /** 对话记忆 TTL（小时） */
        private int ttlHours = 24;
        /** 滑动窗口最大消息数 */
        private int maxMessages = 20;

        public int getTtlHours() { return ttlHours; }
        public void setTtlHours(int ttlHours) { this.ttlHours = ttlHours; }
        public int getMaxMessages() { return maxMessages; }
        public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
    }

    /**
     * RAG 检索相关配置组（开关、向量存储类型、Embedding 模型等）。
     */
    public static class Rag {
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
        private int dimension = 1536;
        /** 分块大小 */
        private int chunkSize = 500;
        /** 分块重叠 */
        private int chunkOverlap = 50;
        /** 检索 Top-K */
        private int topK = 5;
        /** 最小相似度阈值 */
        private double minScore = 0.7;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getVectorStore() { return vectorStore; }
        public void setVectorStore(String vectorStore) { this.vectorStore = vectorStore; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
        public String getEmbeddingApiKey() { return embeddingApiKey; }
        public void setEmbeddingApiKey(String embeddingApiKey) { this.embeddingApiKey = embeddingApiKey; }
        public String getEmbeddingBaseUrl() { return embeddingBaseUrl; }
        public void setEmbeddingBaseUrl(String embeddingBaseUrl) { this.embeddingBaseUrl = embeddingBaseUrl; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
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

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<ServerInfo> getServers() { return servers; }
        public void setServers(List<ServerInfo> servers) { this.servers = servers; }
    }

    /**
     * MCP Server 连接信息
     */
    public static class ServerInfo {
        /** Server 名称（唯一标识） */
        private String name;
        /** 传输类型（sse / stdio） */
        private String transport = "sse";
        /** SSE 端点 URL（transport=sse 时必填） */
        private String url;
        /** 调用超时（秒） */
        private int timeoutSeconds = 30;
        /** 是否启用 */
        private boolean enabled = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
