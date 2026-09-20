package com.njydsz.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置增强属性
 *
 * <p>本模块作为 Jasypt 的增强层，不再自行实现加密逻辑。加密 / 解密由 {@code jasypt-spring-boot-starter} 全局处理，
 * 本属性类仅管理增强功能开关：配置变更监听、CLI 工具参数、健康检查。
 *
 * <p><b>OOP-006-EXEMPT</b>：本类及内部嵌套类标注 {@link ConfigurationProperties}，布尔字段名直接作为 YAML 对外属性键。
 * 重命名 {@code isXxx} 字段将导致 {@code ydsz.config.*} 配置键失效，破坏向后兼容性。因此布尔字段带 {@code is}
 * 前缀，按《云顶编码规范》YDIZ-OOP-006 豁免规则不改为裸名。
 *
 * <p>加密配置请使用 Jasypt 原生属性：
 *
 * <pre>{@code
 * jasypt:
 *   encryptor:
 *     password: ${JASYPT_ENCRYPTOR_PASSWORD}
 *     algorithm: PBEWithHMACSHA512AndAES_256
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.config")
public class ConfigProperties {

  /** 配置变更监听（Nacos/Spring Cloud 配置刷新桥接） */
  private ChangeMonitor changeMonitor = new ChangeMonitor();

  /** CLI 加密工具配置 */
  private Cli cli = new Cli();

  /** 健康检查配置 */
  private Health health = new Health();

  /** 配置变更监听属性 */
  @Getter
  @Setter
  public static class ChangeMonitor {

    /** 是否启用配置变更监听桥接（默认 true） */
    private boolean isEnabled = true;

    /**
     * 是否在变更通知前快照旧值。
     *
     * <p>true（默认）：监听 RefreshEvent 快照旧值，EnvironmentChangeEvent 时 diff 并通知。
     *
     * <p>false：仅通知 key + newValue，oldValue 为 null（减少内存开销）。
     */
    private boolean snapshotOldValues = true;

    /**
     * 是否异步分发监听器回调。
     *
     * <p>true（默认）：通过线程池异步回调监听器，不阻塞 Spring Cloud 刷新主线程。
     *
     * <p>false：同步回调（适用于需要严格保证监听器执行顺序的场景）。
     *
     * <p>对标 Apollo 单线程异步回调与 Nacos 独立线程池分发。
     *
     * @since 26.09.20
     */
    private boolean asyncDispatch = true;

    /**
     * 异步分发线程池核心线程数。
     *
     * <p>默认 2，适合监听器数量 ≤ 10 的场景。监听器数量较多或回调耗时较大时适当调大。
     *
     * @since 26.09.20
     */
    private int asyncCorePoolSize = 2;

    /**
     * 异步分发线程池任务队列容量。
     *
     * <p>默认 256，满载时由调用线程执行（CallerRunsPolicy），避免任务丢失。
     *
     * @since 26.09.20
     */
    private int asyncQueueCapacity = 256;

    /**
     * 是否启用配置变更审计发布（默认 true）。
     *
     * <p>启用后，每次配置变更时将发布一条审计日志，包含节点 IP、变更数量、来源命名空间、租户信息。
     * 审计通过 {@link com.njydsz.common.config.hotreload.ConfigAuditPublisher} SPI 实现，
     * 默认实现 {@link com.njydsz.common.config.hotreload.LogbackAuditPublisher} 以 INFO 日志输出。
     *
     * @since 26.09.20
     */
    private boolean auditEnabled = true;
  }

  /**
   * CLI 加密工具属性
   *
   * <p>用于 {@link com.njydsz.common.config.cli.ConfigCliTool} 命令行工具，默认值与 Jasypt 全局配置对齐。
   */
  @Getter
  @Setter
  public static class Cli {

    /** 是否启用 CLI 工具 Bean（默认 true） */
    private boolean isEnabled = true;

    /**
     * 加密算法（与 Jasypt 配置对齐）。
     *
     * <p>默认 PBEWithHMACSHA512AndAES_256，需 JCE unlimited strength（JDK 8u161+ 内置）。
     * 降级方案：PBEWithMD5AndDES（弱但不需 JCE）。
     */
    private String algorithm = "PBEWithHMACSHA512AndAES_256";

    /** 密钥派生迭代次数（默认 1000，与 Jasypt 默认值一致） */
    private int keyObtentionIterations = 1000;

    /** 加密器池大小（默认 4） */
    private int poolSize = 4;
  }

  /** 健康检查属性 */
  @Getter
  @Setter
  public static class Health {

    /** 是否启用配置加密健康检查（默认 true） */
    private boolean isEnabled = true;

    /**
     * 健康检查缓存 TTL（毫秒）。
     *
     * <p>在此时间内的重复请求直接返回上次结果，避免高频调用全量扫描属性。
     *
     * <p>默认 5000ms，设为 0 禁用缓存。
     */
    private long cacheTtlMs = 5000L;
  }
}
