package com.njydsz.common.file.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.njydsz.common.file.storage.StorageType;

/**
 * 文件存储配置属性类
 *
 * <p>定义文件存储的各种配置参数，支持多种存储平台：本地存储、MinIO、阿里 OSS、 腾讯 COS、七牛云、S3 兼容存储、华为 OBS。
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   file:
 *     type: minio
 *     endpoint: http://localhost:9000
 *     access-key: minioadmin
 *     secret-key: minioadmin
 *     bucket: ydsz-files
 *     domain: https://files.example.com
 *     allowed-suffixes: png,jpg,pdf,docx
 *     max-file-size: 104857600
 *     part-size: 5242880
 * }</pre>
 *
 * <p><b>分片大小：</b>默认 5MB（与 S3 协议对齐）。分片过小会显著增加请求数， 过大则降低并发度与失败恢复效率。
 *
 * <p><b>安全约束：</b>{@code accessKey} / {@code secretKey} 强烈建议通过 环境变量或 Vault 注入，不要硬编码在配置文件中。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see StorageType
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.file")
public class FileProperties {

  /**
   * 是否启用文件存储模块（默认 true）
   *
   * <p>关闭后，{@code FileConfiguration} 及相关 Bean 不会自动注册， {@code FileHealthIndicator} 也需要显式设置为 true
   * 才会启用。
   */
  private boolean enabled = true;

  /**
   * 存储类型
   *
   * <p>可选值：local（本地）、minio、oss / aliyun（阿里云）、cos / tencent-cos（腾讯云）、 qiniu（七牛云）、s3 / aws-s3（AWS S3
   * 兼容）、obs / huawei-obs（华为云）
   */
  private String type = StorageType.MINIO;

  /** 文件服务器地址（云存储的 endpoint，如 http://localhost:9000） */
  private String endpoint;

  /** 访问密钥 AccessKey（用于身份认证；建议从环境变量注入） */
  private String accessKey;

  /** 秘密密钥 SecretKey（用于身份认证；建议从环境变量注入） */
  private String secretKey;

  /** 存储桶名称（用于隔离不同业务的文件） */
  private String bucket;

  /** 访问域名（文件访问的域名或 Nginx 代理地址） */
  private String domain;

  /** 区域信息（云存储的区域配置，部分云存储需要指定区域） */
  private String region;

  /** 允许上传的文件后缀列表（为空时使用默认白名单；配置后以当前列表为准） */
  private List<String> allowedSuffixes = new ArrayList<>(4);

  /** 单文件最大大小（字节，默认 100MB = 104857600 字节） */
  private Long maxFileSize = 104857600L;

  /**
   * 上传内存缓冲阈值（字节，默认 16MB = 16777216 字节）
   *
   * <p>小于等于该阈值的文件在内存中缓冲（多次复用高效）；大于该阈值的文件 落盘到临时文件后以流式复用，避免大文件全量读入内存导致 OOM。
   */
  private long memoryBufferThreshold = 16 * 1024 * 1024;

  /** 最大请求大小（字节，默认 100MB） */
  private long maxRequestSize = 100 * 1024 * 1024;

  /** 连接超时时间（毫秒，默认 30s） */
  private Integer connectionTimeout = 30000;

  /** Socket 超时时间（毫秒，默认 60s） */
  private Integer socketTimeout = 60000;

  /** HTTP 连接池最大连接数（默认 100） */
  @Min(1)
  private Integer maxConnections = 100;

  /** 失败重试次数（默认 3 次） */
  @Min(0)
  @Max(10)
  private Integer retryCount = 3;

  /** 私有 URL 过期时间（秒，默认 1h） */
  private Integer temporarySignatureExpiry = 3600;

  /** 分片大小（字节，默认 5MB） */
  private Long partSize = 5242880L;

  /** 断点续传检查点目录（默认使用系统临时目录） */
  private String checkpointDir;

  /** 是否启用 Magic Number 文件头校验（默认启用；关闭后仅基于后缀名校验） */
  private boolean checkMagicNumber = true;

  /** 健康检查缓存时间（秒，默认 30s）；在此时间内重复请求直接返回上次结果，避免高频轮询对存储后端造成压力 */
  @Min(0)
  @Max(300)
  private int healthCheckIntervalSeconds = 30;

  /** 上传并发控制配置 */
  private ConcurrencyControl concurrencyControl = new ConcurrencyControl();

  /**
   * 下载缓冲区大小（字节，默认 64KB）
   *
   * <p>影响 {@code download()} 方法中字节拷贝的缓冲区大小。 64KB 是业界主流对象存储 SDK（阿里 OSS SDK / Spring Content）的默认值， 在高带宽场景下相比 8KB
   * 能显著减少系统调用次数。
   */
  @Min(4096)
  @Max(1048576)
  private int downloadBufferSize = 64 * 1024;

  /**
   * 分片上传上下文 TTL（秒，默认 24 小时）
   *
   * <p>超时未完成的上传上下文将被 {@link com.njydsz.common.file.config.FileScheduler} 定时清理。
   */
  @Min(60)
  private long multipartContextTtlSeconds = 24 * 3600;

  /**
   * 断点续传检查点 TTL（秒，默认 24 小时）
   *
   * <p>检查点过期后，断点续传将不再可用，需重新上传。
   */
  @Min(60)
  private long checkpointTtlSeconds = 24 * 3600;

  /**
   * 是否启用服务端加密（SSE）
   *
   * <p>启用后上传的对象将在存储后端使用服务端加密保护。 支持算法由 {@link #encryptionAlgorithm} 指定。
   */
  private boolean encryptionEnabled = false;

  /**
   * 服务端加密算法
   *
   * <p>可选值：{@code AES256}（S3 默认）、{@code aws:KMS}（AWS KMS 托管密钥）、 {@code RSA}（RSA 公钥加密，部分云厂商支持）。
   */
  private String encryptionAlgorithm = "AES256";

  /** 上传并发控制配置 */
  @Data
  public static class ConcurrencyControl {

    /** 是否启用并发控制（默认启用） */
    private boolean enabled = true;
  }
}
