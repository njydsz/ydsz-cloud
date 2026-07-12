package com.njydsz.pmis.agent.server.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Agent 配置化定义（P1-6 落地）。
 *
 * <p>对标 Coze Bot 配置面板 / Dify 应用编排配置：
 * <ul>
 *   <li>通过配置定义 Agent 的所有行为参数，无需硬编码</li>
 *   <li>支持运行时动态加载和切换 Agent 配置</li>
 *   <li>支持配置版本管理和灰度发布</li>
 *   <li>支持从 DB / 配置文件 / API 多种来源加载</li>
 * </ul>
 *
 * <p>配置字段说明：
 * <ul>
 *   <li>{@code agentType} - Agent 唯一标识</li>
 *   <li>{@code systemPrompt} - 系统提示词</li>
 *   <li>{@code reasoningMode} - 推理模式（react / plan-execute）</li>
 *   <li>{@code modelConfig} - LLM 模型配置（provider、temperature、maxTokens 等）</li>
 *   <li>{@code tools} - 绑定的工具列表</li>
 *   <li>{@code memoryConfig} - 对话记忆配置</li>
 *   <li>{@code ragConfig} - RAG 检索增强配置</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-6)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Agent 类型（唯一标识，如 FlowGeneratorAgent、RiskAssessAgent） */
    private String agentType;

    /** Agent 显示名称 */
    private String displayName;

    /** Agent 描述 */
    private String description;

    /** 系统提示词（System Prompt） */
    private String systemPrompt;

    /** 用户提示词模板（可选，含 ${param} 占位符） */
    private String userPromptTemplate;

    /** 推理模式：react / plan-execute */
    @Builder.Default
    private String reasoningMode = "react";

    /** 最大推理步数 */
    @Builder.Default
    private int maxSteps = 10;

    /** LLM 模型配置 */
    @Builder.Default
    private ModelConfig modelConfig = ModelConfig.builder().build();

    /** 绑定的工具列表（工具名） */
    private List<String> tools;

    /** 对话记忆配置 */
    @Builder.Default
    private MemoryConfig memoryConfig = MemoryConfig.builder().build();

    /** RAG 检索增强配置 */
    private RagConfig ragConfig;

    /** 是否启用流式输出 */
    @Builder.Default
    private boolean streamingEnabled = true;

    /** 是否启用 HITL 审批 */
    @Builder.Default
    private boolean hitlEnabled = false;

    /** 超时时间（毫秒，0 表示不超时） */
    @Builder.Default
    private long timeoutMs = 60000L;

    /** 重试次数 */
    @Builder.Default
    private int retryCount = 0;

    /** 自定义参数（扩展字段） */
    private Map<String, Object> customParams;

    /** 租户 ID */
    private String tenantId;

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    // ==================== 内部配置类 ====================

    /**
     * LLM 模型配置。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelConfig implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** LLM Provider 名称（如 openai / dashscope） */
        @Builder.Default
        private String provider = "dashscope";

        /** 模型名称（如 qwen-plus / gpt-4o） */
        @Builder.Default
        private String model = "qwen-plus";

        /** 温度参数（0-2，越高越随机） */
        @Builder.Default
        private double temperature = 0.7;

        /** 最大输出 Token 数 */
        @Builder.Default
        private int maxTokens = 2048;

        /** Top-P 采样参数 */
        @Builder.Default
        private double topP = 0.9;

        /** Presence Penalty */
        @Builder.Default
        private double presencePenalty = 0.0;

        /** Frequency Penalty */
        @Builder.Default
        private double frequencyPenalty = 0.0;

        /** 停止词列表 */
        private List<String> stop;
    }

    /**
     * 对话记忆配置。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryConfig implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 是否启用对话记忆 */
        @Builder.Default
        private boolean enabled = true;

        /** 记忆类型：in-memory / redis / summary */
        @Builder.Default
        private String type = "in-memory";

        /** 最大历史消息条数 */
        @Builder.Default
        private int maxHistorySize = 20;

        /** 摘要触发的 Token 阈值（summary 类型使用） */
        @Builder.Default
        private int summaryThreshold = 2000;

        /** Redis Key 前缀（redis 类型使用） */
        @Builder.Default
        private String redisKeyPrefix = "agent:memory:";
    }

    /**
     * RAG 检索增强配置。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagConfig implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 是否启用 RAG */
        @Builder.Default
        private boolean enabled = false;

        /** 知识库 ID */
        private String knowledgeBaseId;

        /** 检索 Top-K */
        @Builder.Default
        private int topK = 5;

        /** 相似度阈值 */
        @Builder.Default
        private double similarityThreshold = 0.7;

        /** 是否启用重排序 */
        @Builder.Default
        private boolean rerankEnabled = false;

        /** 重排序策略：llm / cross-encoder */
        @Builder.Default
        private String rerankStrategy = "cross-encoder";

        /** 是否将检索结果注入 System Prompt */
        @Builder.Default
        private boolean injectToSystemPrompt = true;
    }
}
