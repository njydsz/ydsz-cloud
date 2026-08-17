package com.njydsz.common.audit.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 审计模块配置属性
 *
 * <p>从 {@code application.yml/properties} 中读取 {@code ydsz.audit} 前缀的配置。 支持嵌套配置（{@link
 * AsyncProperties}）和默认敏感词列表。
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   audit:
 *     enabled: true
 *     app-key: my-app
 *     storage-type: LOCAL
 *     record-request: true
 *     record-response: false
 *     sensitive-params: [password, token, secret]
 *     retention-days: 90
 *     async:
 *       batch-size: 100
 *       queue-capacity: 10000
 *       thread-core-size: 2
 *       thread-max-size: 4
 *       reject-policy: DISCARD_OLDEST
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.audit")
public class AuditProperties {

  /** 是否启用审计模块（默认启用） */
  private boolean enabled = true;

  /** 应用标识（多应用场景区分审计数据归属，合并原 appId/appCode/appName） */
  private String appKey;

  /**
   * 存储策略类型（默认 LOCAL）
   *
   * <p>可选值：DEFAULT（默认日志输出）、LOCAL（本地数据库）、REMOTE（远程推送）、MQ（消息队列）
   */
  private String storageType = "LOCAL";

  /** 是否记录请求参数（默认启用，敏感接口需配合 {@code excludeParams} 使用） */
  private boolean recordRequest = true;

  /** 是否记录响应结果（默认禁用，响应体可能较大且含敏感数据） */
  private boolean recordResponse = false;

  /** 是否异步记录（默认异步，避免阻塞业务主链路） */
  private boolean recordAsync = true;

  /**
   * 敏感参数名称列表，命中名称的参数不会被序列化到审计日志
   *
   * <p>默认覆盖常见敏感词：password、token、secret、apiKey、privateKey 等
   */
  private String[] sensitiveParams = {
    "password", "oldPassword", "newPassword", "confirmPassword",
    "token", "accessToken", "refreshToken", "authorization",
    "secret", "apiKey", "privateKey"
  };

  /** 是否启用敏感字段脱敏（默认启用） */
  private boolean maskEnabled = true;

  /** 审计日志保留天数（默认 90 天，超过可通过清理任务归档） */
  private int retentionDays = 90;

  /** 是否启用分表（默认不启用） */
  private boolean shardingEnabled = false;

  /** 分表类型：monthly / daily / yearly（默认 monthly） */
  private String shardingType = "monthly";

  /** 基础表名（默认 sys_audit_log） */
  private String shardingBaseTableName = "sys_audit_log";

  /** 异步批量写入配置 */
  private AsyncProperties async = new AsyncProperties();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getAppKey() {
    return appKey;
  }

  public void setAppKey(String appKey) {
    this.appKey = appKey;
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

  public AsyncProperties getAsync() {
    return async;
  }

  public void setAsync(AsyncProperties async) {
    this.async = async;
  }

  /** 异步批量写入配置属性 */
  public static class AsyncProperties {

    /** 批量写入阈值（条数，达到后立即触发刷盘） */
    private int batchSize = 100;

    /** 批量写入间隔（毫秒），超过此间隔即使未满也会写入 */
    private long batchIntervalMillis = 5000;

    /** 异步队列最大容量（满后按 rejectPolicy 处理） */
    private int queueCapacity = 10000;

    /** 异步线程池核心线程数（默认 2） */
    @Min(1)
    private int threadCoreSize = 2;

    /** 异步线程池最大线程数（默认 4） */
    @Min(1)
    private int threadMaxSize = 4;

    /**
     * 队列满时的拒绝策略（默认 CALLER_RUNS）
     *
     * <p>默认采用 CALLER_RUNS（调用者阻塞等待 + 超时后磁盘兜底），保证审计留痕完整、不静默丢失。
     * 如对审计实时性要求高于完整性，可改为 DISCARD_OLDEST（丢弃最旧日志）或 DISCARD_NEWEST（丢弃最新日志）。
     *
     * <p>可选值：DISCARD_OLDEST（丢弃最旧日志）、DISCARD_NEWEST（丢弃最新日志）、CALLER_RUNS（调用者阻塞等待）
     */
    private String rejectPolicy = "CALLER_RUNS";

    /** 优雅停机超时时间（秒，默认 30s） */
    private long shutdownTimeout = 30;

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

    /**
     * 获取异步队列容量（兼容旧配置）
     *
     * @return 队列容量
     */
    public int getExecutorQueueCapacity() {
      return queueCapacity;
    }

    public int getQueueCapacity() {
      return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
    }

    public int getThreadCoreSize() {
      return threadCoreSize;
    }

    public void setThreadCoreSize(int threadCoreSize) {
      this.threadCoreSize = threadCoreSize;
    }

    public int getThreadMaxSize() {
      return threadMaxSize;
    }

    public void setThreadMaxSize(int threadMaxSize) {
      this.threadMaxSize = threadMaxSize;
    }

    /**
     * 获取线程池核心线程数（兼容旧配置）
     *
     * @return 核心线程数
     */
    public int getCorePoolSize() {
      return threadCoreSize;
    }

    /**
     * 获取线程池最大线程数（兼容旧配置）
     *
     * @return 最大线程数
     */
    public int getMaxPoolSize() {
      return threadMaxSize;
    }

    /**
     * 获取拒绝策略（兼容旧配置）
     *
     * @return 拒绝策略名称
     */
    public String getAsyncRejectPolicy() {
      return rejectPolicy;
    }

    public String getRejectPolicy() {
      return rejectPolicy;
    }

    public void setRejectPolicy(String rejectPolicy) {
      this.rejectPolicy = rejectPolicy;
    }

    public long getShutdownTimeout() {
      return shutdownTimeout;
    }

    public void setShutdownTimeout(long shutdownTimeout) {
      this.shutdownTimeout = shutdownTimeout;
    }
  }
}
