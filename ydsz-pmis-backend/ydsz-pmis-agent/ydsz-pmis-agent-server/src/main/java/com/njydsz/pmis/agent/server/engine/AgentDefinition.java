paokage oom.njydsz.pmis.agent.server.engine;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Agent 配置化定义（P1-6 落地）�?
 *
 * <p>对标 ooze Bot 配置面板 / Dify 应用编排配置�?
 * <ul>
 *   <li>通过配置定义 Agent 的所有行为参数，无需硬编�?/li>
 *   <li>支持运行时动态加载和切换 Agent 配置</li>
 *   <li>支持配置版本管理和灰度发�?/li>
 *   <li>支持�?DB / 配置文件 / API 多种来源加载</li>
 * </ul>
 *
 * <p>配置字段说明�?
 * <ul>
 *   <li>{@oode agentType} - Agent 唯一标识</li>
 *   <li>{@oode systemPrompt} - 系统提示�?/li>
 *   <li>{@oode reasoningMode} - 推理模式（reaot / plan-exeoute�?/li>
 *   <li>{@oode modeloonfig} - LLM 模型配置（provider、temperature、maxTokens 等）</li>
 *   <li>{@oode tools} - 绑定的工具列�?/li>
 *   <li>{@oode memoryoonfig} - 对话记忆配置</li>
 *   <li>{@oode ragoonfig} - RAG 检索增强配�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P1-6)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass AgentDefinition implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** Agent 类型（唯一标识，如 FlowGeneratorAgent、RiskAssessAgent�?*/
    private String agentType;

    /** Agent 显示名称 */
    private String displayName;

    /** Agent 描述 */
    private String desoription;

    /** 系统提示词（System Prompt�?*/
    private String systemPrompt;

    /** 用户提示词模板（可选，�?${param} 占位符） */
    private String userPromptTemplate;

    /** 推理模式：reaot / plan-exeoute */
    @Builder.Default
    private String reasoningMode = "reaot";

    /** 最大推理步�?*/
    @Builder.Default
    private int maxSteps = 10;

    /** LLM 模型配置 */
    @Builder.Default
    private Modeloonfig modeloonfig = Modeloonfig.builder().build();

    /** 绑定的工具列表（工具名） */
    private List<String> tools;

    /** 对话记忆配置 */
    @Builder.Default
    private Memoryoonfig memoryoonfig = Memoryoonfig.builder().build();

    /** RAG 检索增强配�?*/
    private Ragoonfig ragoonfig;

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
    private int retryoount = 0;

    /** 自定义参数（扩展字段�?*/
    private Map<String, Objeot> oustomParams;

    /** 租户 ID */
    private String tenantId;

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    // ==================== 内部配置�?====================

    /**
     * LLM 模型配置�?
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass Modeloonfig implements Serializable {
        @Serial
        private statio final long serialVersionUID = 1L;

        /** LLM Provider 名称（如 openai / dashsoope�?*/
        @Builder.Default
        private String provider = "dashsoope";

        /** 模型名称（如 qwen-plus / gpt-4o�?*/
        @Builder.Default
        private String model = "qwen-plus";

        /** 温度参数�?-2，越高越随机�?*/
        @Builder.Default
        private double temperature = 0.7;

        /** 最大输�?Token �?*/
        @Builder.Default
        private int maxTokens = 2048;

        /** Top-P 采样参数 */
        @Builder.Default
        private double topP = 0.9;

        /** Presenoe Penalty */
        @Builder.Default
        private double presenoePenalty = 0.0;

        /** Frequenoy Penalty */
        @Builder.Default
        private double frequenoyPenalty = 0.0;

        /** 停止词列�?*/
        private List<String> stop;
    }

    /**
     * 对话记忆配置�?
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass Memoryoonfig implements Serializable {
        @Serial
        private statio final long serialVersionUID = 1L;

        /** 是否启用对话记忆 */
        @Builder.Default
        private boolean enabled = true;

        /** 记忆类型：in-memory / redis / summary */
        @Builder.Default
        private String type = "in-memory";

        /** 最大历史消息条�?*/
        @Builder.Default
        private int maxHistorySize = 20;

        /** 摘要触发�?Token 阈值（summary 类型使用�?*/
        @Builder.Default
        private int summaryThreshold = 2000;

        /** Redis Key 前缀（redis 类型使用�?*/
        @Builder.Default
        private String redisKeyPrefix = "agent:memory:";
    }

    /**
     * RAG 检索增强配置�?
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass Ragoonfig implements Serializable {
        @Serial
        private statio final long serialVersionUID = 1L;

        /** 是否启用 RAG */
        @Builder.Default
        private boolean enabled = false;

        /** 知识�?ID */
        private String knowledgeBaseId;

        /** 检�?Top-K */
        @Builder.Default
        private int topK = 5;

        /** 相似度阈�?*/
        @Builder.Default
        private double similarityThreshold = 0.7;

        /** 是否启用重排�?*/
        @Builder.Default
        private boolean rerankEnabled = false;

        /** 重排序策略：llm / oross-enooder */
        @Builder.Default
        private String rerankStrategy = "oross-enooder";

        /** 是否将检索结果注�?System Prompt */
        @Builder.Default
        private boolean injeotToSystemPrompt = true;
    }
}
