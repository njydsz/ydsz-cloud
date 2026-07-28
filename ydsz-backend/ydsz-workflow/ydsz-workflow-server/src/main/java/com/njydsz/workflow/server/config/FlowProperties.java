package com.njydsz.workflow.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 工作流模块统一配置属性。
 *
 * <p>配置前缀：{@code ydsz.flow}
 *
 * <p>典型配置示例：
 * <pre>
 * ydsz:
 *   flow:
 *     enabled: true
 *     health-enabled: true
 *     default-tenant-id: "1"
 *     publish-block-on-high-risk: true
 *     designer-lock-timeout-minutes: 30
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.flow")
public class FlowProperties {

    /** 是否启用工作流模块 */
    private boolean enabled = true;

    /** 是否启用健康检查 */
    private boolean healthEnabled = true;

    /** 默认租户 ID（用于无 AuthContext 时的回退值） */
    private String defaultTenantId = "1";

    /** 发布流程时是否阻断 HIGH 风险（在途实例卡在已删除节点）。true=阻断（推荐）；false=仅警告 */
    private boolean publishBlockOnHighRisk = true;

    /** 设计器协同编辑锁定超时阈值（分钟） */
    @Min(1)
    private long designerLockTimeoutMinutes = 30L;

    /** 流程定义缓存 TTL（分钟），默认 30 */
    @Min(1)
    private int definitionCacheTtlMinutes = 30;

    /** 单实例最大并发流程数（0=不限） */
    @Min(0)
    private int maxConcurrentInstances = 0;

    /** 任务默认超时时间（小时，0=不超时） */
    @Min(0)
    private int defaultTaskTimeoutHours = 0;

    /** 会签默认投票通过率（0-100） */
    @Min(0)
    private int defaultVotePassRate = 50;

    /** 催办最小间隔（分钟），防止频繁催办 */
    @Min(1)
    private int urgeMinIntervalMinutes = 30;

    /** P3-3.4: 三方审批配置（钉钉/飞书/企微 Webhook 签名密钥 + 重试策略） */
    private ThirdParty thirdParty = new ThirdParty();

    /** P3-3.4: 自动催办配置 */
    private AutoUrge autoUrge = new AutoUrge();

    /** P3-3.4: 子流程嵌套配置 */
    private SubProcess subProcess = new SubProcess();

    /** P3-3.4: 附件预览配置 */
    private Attachment attachment = new Attachment();

    /**
     * P3-3.4: 三方审批配置。
     *
     * <p>包含钉钉/飞书/企微 Webhook 回调的签名校验密钥，
     * 以及三方回调失败重试策略（由 {@link com.njydsz.workflow.server.job.FlowThirdPartyRetryJobHandler} 消费）。
     */
    @Data
    public static class ThirdParty {
        /** 三方回调重试配置 */
        private Retry retry = new Retry();
        /** 钉钉应用 appSecret（签名校验密钥） */
        private DingTalk dingtalk = new DingTalk();
        /** 飞书应用 appSecret（签名校验密钥） */
        private Feishu feishu = new Feishu();
        /** 企微回调 Token（签名校验密钥） */
        private WeCom wecom = new WeCom();

        @Data
        public static class Retry {
            /** 最大重试次数（超过则进入死信不再扫描） */
            private int maxRetries = 3;
            /** 单批扫描条数 */
            private int batchSize = 50;
            /** 集群锁持有时间（秒） */
            private int lockLeaseSec = 120;
        }

        @Data
        public static class DingTalk {
            /** 钉钉应用 appSecret */
            private String appSecret = "";
        }

        @Data
        public static class Feishu {
            /** 飞书应用 appSecret */
            private String appSecret = "";
        }

        @Data
        public static class WeCom {
            /** 企微回调 Token */
            private String token = "";
        }
    }

    /**
     * P3-3.4: 自动催办配置。
     *
     * <p>由 {@link com.njydsz.workflow.server.scheduler.FlowAutoUrgeScheduler} 消费。
     */
    @Data
    public static class AutoUrge {
        /** 自动催办阈值（小时），任务创建后超过此时间未处理则触发催办 */
        private long thresholdHours = 24;
        /** 最大催办次数 */
        private int maxCount = 3;
        /** 每次扫描批量大小 */
        private int batchSize = 200;
    }

    /**
     * P3-3.4: 子流程嵌套配置。
     *
     * <p>由 {@link com.njydsz.workflow.server.service.impl.instance.FlowSubProcessServiceImpl} 消费。
     */
    @Data
    public static class SubProcess {
        /** 最大子流程嵌套深度（默认 3 层，建议不超过 10 层） */
        private int maxNestingDepth = 3;
    }

    /**
     * P3-3.4: 附件预览配置。
     *
     * <p>由 {@link com.njydsz.workflow.server.service.impl.integration.FlowAttachmentServiceImpl} 消费。
     */
    @Data
    public static class Attachment {
        /** 外部预览服务地址（kkFileView/Office Online），如 http://preview.example.com/onlinePreview?url={url} */
        private String previewServerUrl = "";
    }
}
