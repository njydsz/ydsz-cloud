package com.njydsz.nextwiki.domain.event;

import org.springframework.context.ApplicationEvent;

/**
 * 文件版本快照创建事件 — 文件上传/秒传成功后发布，由监听器在事务提交后异步创建文件版本记录。
 *
 * <p><b>设计意图（云顶编码规范 35.2 版本快照异步化）：</b>
 *
 * <ul>
 *   <li>将文件版本记录创建从主写操作事务中剥离，缩短主事务持锁时间，降低上传操作延迟
 *   <li>使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 保证仅在主事务提交成功后创建版本记录，
 *       避免回滚事务产生垃圾版本记录
 *   <li>版本创建失败不影响主业务（监听器内部捕获异常仅日志告警）
 * </ul>
 *
 * <p><b>发布时机：</b>由 {@code FileApplicationService} 在文件上传/秒传成功后、事务提交前发布。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FileVersionSnapshotEvent extends ApplicationEvent {

  /** 文件节点 ID */
  private final String fileNodeId;

  /** 存储对象键 */
  private final String storageKey;

  /** 文件大小（字节） */
  private final Long size;

  /** 文件 SHA-256 哈希 */
  private final String fileHash;

  /** MIME 类型 */
  private final String mimeType;

  /** 版本备注 */
  private final String remark;

  /** 操作人 ID */
  private final String userId;

  /**
   * 构造文件版本快照事件。
   *
   * @param source 事件源（通常为发布者的 {@code this} 引用）
   * @param fileNodeId 文件节点 ID
   * @param storageKey 存储对象键
   * @param size 文件大小（字节）
   * @param fileHash 文件 SHA-256 哈希
   * @param mimeType MIME 类型
   * @param remark 版本备注
   * @param userId 操作人 ID
   */
  public FileVersionSnapshotEvent(
      Object source,
      String fileNodeId,
      String storageKey,
      Long size,
      String fileHash,
      String mimeType,
      String remark,
      String userId) {
    super(source);
    this.fileNodeId = fileNodeId;
    this.storageKey = storageKey;
    this.size = size;
    this.fileHash = fileHash;
    this.mimeType = mimeType;
    this.remark = remark;
    this.userId = userId;
  }

  public String getFileNodeId() {
    return fileNodeId;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public Long getSize() {
    return size;
  }

  public String getFileHash() {
    return fileHash;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getRemark() {
    return remark;
  }

  public String getUserId() {
    return userId;
  }
}
