package com.njydsz.common.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import lombok.Data;

/**
 * Web 端文件上传（Multipart）配置属性
 *
 * <p>配置前缀：{@code ydsz.web.multipart}
 *
 * <p><b>设计动机：</b>Spring Boot 默认 {@code spring.servlet.multipart} 的 {@code max-file-size=1MB} /
 * {@code max-request-size=10MB} 对企业级业务应用偏小， 一次文件上传就会触发 {@code MaxUploadSizeExceededException}。
 * 本配置类提供更合理的默认值（50MB / 100MB），并暴露统一配置入口。
 *
 * <p><b>覆盖关系：</b>启用本配置后，会注册一个 {@link jakarta.servlet.MultipartConfigElement}
 * Bean（{@code @ConditionalOnMissingBean}），优先级高于 Spring Boot 默认。 业务方仍可通过自定义 {@code
 * MultipartConfigElement} Bean 进一步覆盖。
 *
 * <p><b>配置示例（YAML）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   web:
 *     multipart:
 *       enabled: true              # 启用统一 multipart 配置（默认 true）
 *       max-file-size: 50MB       # 单文件最大大小（默认 50MB）
 *       max-request-size: 100MB   # 整个请求最大大小（默认 100MB）
 *       file-size-threshold: 0    # 写入磁盘的阈值（默认 0，即全部内存）
 *       resolve-lazily: false     # 是否延迟解析（默认 false，请求时立即解析）
 *       location: ""              # 临时文件目录（默认空，使用 Servlet 容器默认）
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.web.multipart")
public class WebMultipartProperties {

  /**
   * 是否启用统一 multipart 配置。
   *
   * <p>启用后会注册一个 {@link jakarta.servlet.MultipartConfigElement} Bean， 覆盖 Spring Boot 默认的 1MB / 10MB
   * 限制。 默认值：{@code true}
   */
  private boolean enabled = true;

  /**
   * 单个文件最大大小。
   *
   * <p>支持数据量字符串（如 {@code 50MB}、{@code 1024KB}）或字节数。 默认值：{@code 50MB}（52428800 字节）
   */
  private DataSize maxFileSize = DataSize.ofMegabytes(50);

  /**
   * 整个 multipart 请求最大大小（包含所有文件 + 表单字段）。
   *
   * <p>支持数据量字符串或字节数。 默认值：{@code 100MB}（104857600 字节）
   */
  private DataSize maxRequestSize = DataSize.ofMegabytes(100);

  /**
   * 文件大小阈值，超过此大小后写入磁盘临时文件。
   *
   * <p>设为 0 表示所有文件都先存内存（适合小文件场景）。 默认值：{@code 0}
   */
  private DataSize fileSizeThreshold = DataSize.ofBytes(0);

  /**
   * 是否延迟解析 multipart。
   *
   * <p>设为 true 时，multipart 在被访问时才解析，可在异常处理中重试。 默认值：{@code false}
   */
  private boolean resolveLazily = false;

  /**
   * 临时文件目录。
   *
   * <p>空字符串表示使用 Servlet 容器默认临时目录。 默认值：空字符串
   */
  private String location = "";
}
