package com.njydsz.common.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件上传配置属性
 *
 * <p>绑定前缀 ydsz.file.upload.*，控制文件上传的行为开关：是否启用分片 MD5 校验、
 * 分片大小限制、临时文件清理策略等。
 *
 * <p>配置模式：
 * <pre>
 * ydsz.file.upload:
 *   chunk-md5-check: false
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.file.upload")
public class FileUploadProperties {

  /**
   * 是否启用分片 MD5 校验
   *
   * <p>启用后，每次分片上传时会计算并保存分片的 MD5， 合并完成时会校验整个文件的 MD5。 默认 false。
   */
  private boolean chunkMd5Check = false;
}
