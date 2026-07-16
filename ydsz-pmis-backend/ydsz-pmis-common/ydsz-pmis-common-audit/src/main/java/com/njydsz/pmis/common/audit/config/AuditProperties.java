package com.njydsz.pmis.common.audit.config;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 审计模块配置属性
 * <p>
 * 从 {@code application.yml/properties} 中读取 {@code ydsz.audit} 前缀的配置。
 * 支持嵌套配置（{@link AsyncProperties}）和默认敏感词列表。
 * </p>
 *
 * <p>配置示例：</p>
 * <pre>{@code
 * ydsz:
 *   audit:
 *     enabled: true
 *     storage-type: LOCAL
 *     record-request: true
 *     record-response: false
 *     sensitive-params: [password, token, secret]
 *     retention-days: 90
 *     executor-queue-capacity: 200
 *     async:
 *       batch-size: 100
 *       queue-capacity: 10000
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.audit")
public class AuditProperties {

    /**
     * 是否启用审计模块（默认启用）
     */
    private boolean enabled = true;

    /**
     * 应用 ID（多应用场景区分审计数据归属）
     */
    private String appId;

    /**
     * 应用编码
     */
    private String appCode;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 存储策略类型（默认 DEFAULT）
     * <p>可选值：DEFAULT（默认日志输出）、LOCAL（本地数据库）、REMOTE（远程推送）、MQ（消息队列）
     */
    private String storageType = "DEFAULT";

    /**
     * 是否记录请求参数（默认启用，敏感接口需配合 {@code excludeParams} 使用）
     */
    private boolean recordRequest = true;

    /**
     * 是否记录响应结果（默认禁用，响应体可能较大且含敏感数据）
     */
    private boolean recordResponse = false;

    /**
     * 是否异步记录（默认异步，避免阻塞业务主链路）
     */
    private boolean recordAsync = true;

    /**
     * 敏感参数名称列表，命中名称的参数不会被序列化到审计日志
     * <p>默认覆盖常见敏感词：password、token、secret、apiKey、privateKey 等
     */
    private String[] sensitiveParams = {"password", "oldPassword", "newPassword", "confirmPassword", "token", "accessToken", "refreshToken", "authorization", "secret", "apiKey", "privateKey"};

    /**
     * 是否启用 IP 归属地解析（默认禁用，需要外部 IP 库支持）
     */
    private boolean ipLocationEnabled = false;

    /**
     * 是否启用 User-Agent 解析（默认禁用，UA 字段较长）
     */
    private boolean userAgentEnabled = false;

    /**
     * 批量刷新间隔（毫秒，默认 3000ms）
     */
    private long batchFlushInterval = 3000;

    /**
     * 异步队列满时的拒绝策略（默认 DISCARD_OLDEST）
     * <p>可选值：DISCARD_OLDEST（丢弃最旧日志）、DISCARD_NEWEST（丢弃最新日志）、CALLER_RUNS（调用者阻塞等待）
     */
    private String asyncRejectPolicy = "DISCARD_OLDEST";

    /**
     * 优雅停机超时时间（秒，默认 30s）
     */
    private long asyncShutdownTimeout = 30;

    /**
     * 是否启用敏感字段脱敏（默认启用）
     */
    private boolean maskEnabled = true;

    /** 审计日志保留天数（默认 90 天，超过可通过清理任务归档） */
    private int retentionDays = 90;

    /** 异步记录线程池核心线程数 */
    @Min(1)
    private int corePoolSize = 2;

    /** 异步记录线程池最大线程数 */
    @Min(1)
    private int maxPoolSize = 4;

    /** 异步记录线程池等待队列容量（用于 ThreadPoolTaskExecutor 的工作队列） */
    private int executorQueueCapacity = 200;

    /** 是否启用分表（默认不启用） */
    private boolean shardingEnabled = false;

    /** 分表类型：monthly / daily / yearly（默认 monthly） */
    private String shardingType = "monthly";

    /** 基础表名（默认 sys_audit_log） */
    private String shardingBaseTableName = "sys_audit_log";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public boolean isRecordRequest() {
        return recordRequest;
    }

    public void setRecordRequest(boolean recordRequest) {
        this.recordRequest = recordRequest;
    }

    public boolean isRecordResponse() {
        return recordResponse;
    }

    public void setRecordResponse(boolean recordResponse) {
        this.recordResponse = recordResponse;
    }

    public boolean isRecordAsync() {
        return recordAsync;
    }

    public void setRecordAsync(boolean recordAsync) {
        this.recordAsync = recordAsync;
    }

    public String[] getSensitiveParams() {
        return sensitiveParams;
    }

    public void setSensitiveParams(String[] sensitiveParams) {
        this.sensitiveParams = sensitiveParams;
    }

    public boolean isIpLocationEnabled() {
        return ipLocationEnabled;
    }

    public void setIpLocationEnabled(boolean ipLocationEnabled) {
        this.ipLocationEnabled = ipLocationEnabled;
    }

    public boolean isUserAgentEnabled() {
        return userAgentEnabled;
    }

    public void setUserAgentEnabled(boolean userAgentEnabled) {
        this.userAgentEnabled = userAgentEnabled;
    }

    public long getBatchFlushInterval() {
        return batchFlushInterval;
    }

    public void setBatchFlushInterval(long batchFlushInterval) {
        this.batchFlushInterval = batchFlushInterval;
    }

    public String getAsyncRejectPolicy() {
        return asyncRejectPolicy;
    }

    public void setAsyncRejectPolicy(String asyncRejectPolicy) {
        this.asyncRejectPolicy = asyncRejectPolicy;
    }

    public long getAsyncShutdownTimeout() {
        return asyncShutdownTimeout;
    }

    public void setAsyncShutdownTimeout(long asyncShutdownTimeout) {
        this.asyncShutdownTimeout = asyncShutdownTimeout;
    }

    public boolean isMaskEnabled() {
        return maskEnabled;
    }

    public void setMaskEnabled(boolean maskEnabled) {
        this.maskEnabled = maskEnabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * @deprecated 使用 {@link #getExecutorQueueCapacity()} 替代
     */
    @Deprecated
    public int getQueueCapacity() {
        return executorQueueCapacity;
    }

    /**
     * @deprecated 使用 {@link #setExecutorQueueCapacity(int)} 替代
     */
    @Deprecated
    public void setQueueCapacity(int queueCapacity) {
        this.executorQueueCapacity = queueCapacity;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }

    public boolean isShardingEnabled() {
        return shardingEnabled;
    }

    public void setShardingEnabled(boolean shardingEnabled) {
        this.shardingEnabled = shardingEnabled;
    }

    public String getShardingType() {
        return shardingType;
    }

    public void setShardingType(String shardingType) {
        this.shardingType = shardingType;
    }

    public String getShardingBaseTableName() {
        return shardingBaseTableName;
    }

    public void setShardingBaseTableName(String shardingBaseTableName) {
        this.shardingBaseTableName = shardingBaseTableName;
    }

    /**
     * 异步批量写入配置
     */
    private AsyncProperties async = new AsyncProperties();

    public AsyncProperties getAsync() {
        return async;
    }

    public void setAsync(AsyncProperties async) {
        this.async = async;
    }

    /**
     * 异步批量写入配置属性
     */
    public static class AsyncProperties {

        /**
         * 批量写入阈值（条数，达到后立即触发刷盘）
         */
        private int batchSize = 100;

        /**
         * 批量写入间隔（毫秒），超过此间隔即使未满也会写入
         */
        private long batchIntervalMillis = 5000;

        /**
         * 异步队列最大容量（满后按 {@code asyncRejectPolicy} 处理）
         */
        private int queueCapacity = 10000;

        /**
         * Disruptor WaitStrategy 策略名称（可选值：blocking / sleeping / yielding，默认 blocking）
         */
        private String waitStrategy = "blocking";

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getBatchIntervalMillis() {
            return batchIntervalMillis;
        }

        public void setBatchIntervalMillis(long batchIntervalMillis) {
            this.batchIntervalMillis = batchIntervalMillis;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getWaitStrategy() {
            return waitStrategy;
        }

        public void setWaitStrategy(String waitStrategy) {
            this.waitStrategy = waitStrategy;
        }
    }
}
