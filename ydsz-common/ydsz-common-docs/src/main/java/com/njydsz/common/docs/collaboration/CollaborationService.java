package com.njydsz.common.docs.collaboration;

import java.util.List;

/**
 * 协作文档服务骨架。
 *
 * <p>提供实时协同编辑的锁、版本、操作转换（OT）基础接口。
 * 后续对接 OnlyOffice / Yjs / OT.js 具体实现。
 *
 * @author ydsz-team
 * @since 26.10.14
 */
public interface CollaborationService {

  /**
   * 获取文档当前锁持有者（null 表示未锁定）。
   *
   * @param documentId 文档 ID
   * @param userId 请求锁的用户 ID
   * @return 文档锁信息，未锁定时返回 {@code null}
   */
  DocumentLock acquireLock(String documentId, String userId);

  /**
   * 释放文档锁。
   *
   * @param documentId 文档 ID
   * @param userId 请求释放锁的用户 ID
   */
  void releaseLock(String documentId, String userId);

  /**
   * 提交文档操作（OT 操作）。
   *
   * @param documentId 文档 ID
   * @param operation 操作信息
   */
  void submitOperation(String documentId, DocumentOperation operation);

  /**
   * 获取文档操作历史。
   *
   * @param documentId 文档 ID
   * @param sinceVersion 起始版本号（返回此版本之后的操作）
   * @return 操作历史列表
   */
  List<DocumentOperation> getOperations(String documentId, long sinceVersion);

  /**
   * 文档锁信息。
   *
   * @param documentId 文档 ID
   * @param userId 锁持有者用户 ID
   * @param lockedAt 锁定时间戳（epoch ms）
   * @param expiresAt 过期时间戳（epoch ms）
   */
  record DocumentLock(String documentId, String userId, long lockedAt, long expiresAt) {}

  /**
   * 文档操作信息。
   *
   * @param userId 操作用户 ID
   * @param type 操作类型（insert/delete/retain）
   * @param payload 操作内容 JSON
   * @param timestamp 操作时间戳（epoch ms）
   * @param version 操作版本号（单调递增）
   */
  record DocumentOperation(String userId, String type, String payload, long timestamp, long version) {}
}
