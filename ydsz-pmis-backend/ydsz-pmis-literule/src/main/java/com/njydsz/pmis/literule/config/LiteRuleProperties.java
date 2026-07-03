package com.njydsz.pmis.literule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LiteRule 配置属性
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.literule")
public class LiteRuleProperties {

    /** 是否启用自动注册内置规则 */
    private boolean autoRegisterBuiltinRules = true;

    /** 是否启用规则热加载（监听 RuleConfigRefreshEvent） */
    private boolean hotReloadEnabled = true;

    /** 是否启用执行统计 */
    private boolean statsEnabled = true;

    /** 是否启用 dry-run 仿真 */
    private boolean dryRunEnabled = true;

    /** 是否启用表达式沙箱（限制危险函数和类访问） */
    private boolean sandboxEnabled = true;

    /** 是否启用执行轨迹记录（1.4.0） */
    private boolean traceEnabled = true;

    /** 异步 Trace 队列容量 */
    private int traceQueueCapacity = 5000;

    /** 异步 Trace 批量写入大小 */
    private int traceBatchSize = 100;

    /** 异步 Trace 刷新间隔（毫秒） */
    private long traceFlushIntervalMs = 2000;

    /** 单规则执行超时（毫秒，0 表示不限制，1.4.0） */
    private long ruleTimeoutMs = 0;

    /** 规则熔断错误率阈值（0~1.0，达到阈值时熔断该规则，1.4.0） */
    private double circuitBreakerErrorRate = 0.5;

    /** 规则熔断最小评估次数（达到该次数后才计算错误率，1.4.0） */
    private int circuitBreakerMinEvaluations = 100;

    /**
     * 是否启用规则灰度路由（1.4.0）
     *
     * <p>启用后，对带 canaryRatio > 0 且配置了候选表达式的规则，
     * 按比例将流量分到候选版本，结果会被标记 canary=true。
     */
    private boolean canaryEnabled = true;

    /**
     * 是否启用规则冲突检测（1.4.0）
     *
     * <p>启用后，规则保存前会检测与现有规则的潜在冲突
     * （条件重复、严重度矛盾、命名冲突）。
     */
    private boolean conflictDetectionEnabled = true;

    /**
     * ERROR 级别冲突是否阻塞保存（1.4.0）
     *
     * <p>true：检测到 CONTRADICTORY_SEVERITY 等确定性冲突时抛异常阻塞保存；
     * false：仅记录日志，不阻塞保存。
     */
    private boolean conflictDetectionBlockOnError = true;

    /**
     * AI 增强配置（P2-15）
     */
    private Ai ai = new Ai();

    /**
     * 分布式执行配置（P2-16）
     */
    private Distributed distributed = new Distributed();

    /**
     * AI 增强配置
     *
     * <p>支持自然语言转规则表达式、规则推荐、健康度评分。
     * LLM 客户端通过 OpenAI 兼容协议接入，可在不修改代码的情况下
     * 切换 OpenAI / DeepSeek / 通义千问 / Ollama 等不同提供方。
     */
    @Data
    public static class Ai {

        /** 是否启用 AI 增强 */
        private boolean enabled = false;

        /** LLM 客户端类型：OPENAI_COMPATIBLE / MOCK（默认 MOCK，便于开发） */
        private String llmClient = "MOCK";

        /** LLM API 地址（OpenAI 兼容协议 chat/completions 端点） */
        private String llmApiUrl = "https://api.openai.com/v1/chat/completions";

        /** LLM API Key */
        private String llmApiKey = "";

        /** LLM 模型名称 */
        private String llmModel = "gpt-4o-mini";

        /** LLM 调用超时（毫秒） */
        private long llmTimeoutMs = 15000;

        /** LLM 调用温度（0~1.0，越低越稳定） */
        private double llmTemperature = 0.2;

        /** 健康度评分：命中率权重（0~1.0） */
        private double healthHitRateWeight = 0.30;

        /** 健康度评分：错误率权重（0~1.0） */
        private double healthErrorRateWeight = 0.30;

        /** 健康度评分：复杂度权重（0~1.0） */
        private double healthComplexityWeight = 0.20;

        /** 健康度评分：覆盖率权重（0~1.0） */
        private double healthCoverageWeight = 0.20;

        /** 健康度评分：复杂度上限（表达式 token 数，超过该值视为复杂） */
        private int healthComplexityThreshold = 80;

        /** 推荐结果最大返回条数 */
        private int recommendTopN = 10;
    }

    /**
     * 分布式执行配置
     *
     * <p>启用后规则引擎按一致性 hash 将规则分片到集群节点，
     * 每个节点只执行属于自己的规则，避免重复计算。
     *
     * @since 1.5.0
     */
    @Data
    public static class Distributed {

        /** 是否启用分布式分片执行 */
        private boolean enabled = false;

        /** 虚拟节点数（默认 150，越大越均匀） */
        private int virtualNodes = 150;

        /** 节点列表刷新间隔（毫秒） */
        private long refreshIntervalMs = 10_000L;

        /** 心跳超时时间（毫秒，超过此时间未心跳的节点视为下线） */
        private long heartbeatTimeoutMs = 30_000L;

        /** 心跳发送间隔（毫秒） */
        private long heartbeatIntervalMs = 5_000L;
    }
}
