package com.njydsz.nextwiki.server.service.upload;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传管道上下文。
 *
 * <p>贯穿整个上传流程的请求数据与中间状态容器。每个 {@link UploadStep} 通过它读取输入参数、写入输出结果，
 * 后续步骤通过它获取前置步骤生成的数据（如 fileHash、storageKey）。
 *
 * <p><b>线程安全：</b>每个上传请求创建独立的 UploadContext，无跨请求共享，无需同步。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UploadContext {

  // ==================== 输入参数（步骤开始前置位） ====================

  /** 上传文件 */
  private final MultipartFile file;

  /** 目标父目录节点 ID */
  private final String parentId;

  /** 自定义文件名（可为 null） */
  private final String rename;

  /** 版本备注（可为 null） */
  private final String versionRemark;

  /** 操作人 ID */
  private final String userId;

  // ==================== 中间状态（步骤执行过程中产生） ====================

  /** 安全处理后的文件名 */
  private String fileName;

  /** 文件后缀（小写，不含点） */
  private String suffix;

  /** 解析后的父目录节点 ID */
  private String resolvedParentId;

  /** 父目录路径 */
  private String parentPath;

  /** 父目录层级 + 1 */
  private int level;

  /** 文件 SHA-256 哈希（秒传去重用） */
  private String fileHash;

  /** 存储 key（上传后由存储层返回） */
  private String storageKey;

  /** 存储 bucket */
  private String bucketName;

  /** MIME 类型 */
  private String mimeType;

  /** 已解决的唯一文件名（冲突处理后） */
  private String uniqueFileName;

  /** 秒传命中的已有节点 ID（如命中去重） */
  private String deduplicatedNodeId;

  /** 是否为覆盖模式 */
  private boolean overwriteMode = false;

  /** 被覆盖的旧文件 storageKey（覆盖模式用，用于后续清理） */
  private String overwrittenStorageKey;

  /** 附件属性（用于步骤间传递任意元数据） */
  private final Map<String, Object> attributes = new HashMap<>();

  public UploadContext(
      MultipartFile file, String parentId, String rename, String versionRemark, String userId) {
    this.file = file;
    this.parentId = parentId;
    this.rename = rename;
    this.versionRemark = versionRemark;
    this.userId = userId;
  }

  /**
   * 设置附件属性。
   *
   * @param key 属性名
   * @param value 属性值
   */
  public void setAttribute(String key, Object value) {
    attributes.put(key, value);
  }

  /**
   * 获取附件属性。
   *
   * @param key 属性名
   * @param type 期望类型
   * @return 属性值；不存在或类型不匹配返回 {@code null}
   */
  @SuppressWarnings("unchecked")
  public <T> T getAttribute(String key, Class<T> type) {
    Object value = attributes.get(key);
    if (value != null && type.isInstance(value)) {
      return (T) value;
    }
    return null;
  }

  /**
   * 获取文件 InputStream（便捷方法）。
   *
   * @return 文件输入流
   * @throws java.io.IOException 流读取失败时抛出
   */
  public InputStream getInputStream() throws java.io.IOException {
    return file.getInputStream();
  }

  /**
   * 获取文件大小。
   *
   * @return 文件字节数
   */
  public long getFileSize() {
    return file.getSize();
  }
}
