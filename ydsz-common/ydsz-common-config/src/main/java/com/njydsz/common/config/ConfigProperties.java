package com.njydsz.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置增强属性
 *
 * <p>本模块作为 Jasypt 的增强层，不再自行实现加密逻辑。 加密 / 解密由 {@code jasypt-spring-boot-starter} 全局处理，
 * 本属性类仅管理增强功能开关：配置变更监听、CLI 工具参数、健康检查。
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
 * @since 1.0.0
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
    private boolean enabled = true;

    /**
     * 是否在变更通知前快照旧值。
     *
     * <p>true（默认）：监听 RefreshEvent 快照旧值，EnvironmentChangeEvent 时 diff 并通知。
     *
     * <p>false：仅通知 key + newValue，oldValue 为 null（减少内存开销）。
     */
    private boolean snapshotOldValues = true;
  }

  /**
   * CLI 加密工具属性
   *
   * <p>用于 {@link com.njydsz.common.config.cli.ConfigCliTool} 命令行工具， 默认值与 Jasypt 全局配置对齐。
   */
  @Getter
  @Setter
  public static class Cli {

    /** 是否启用 CLI 工具 Bean（默认 true） */
    private boolean enabled = true;

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
    private boolean enabled = true;

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
