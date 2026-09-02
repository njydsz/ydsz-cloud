package com.njydsz.nextwiki.domain.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计事件记录。
 *
 * <p>封装结构化审计日志的所有字段，供 ELK/Loki 日志平台采集检索。
 * 使用 JSON 库序列化，避免字符串拼接导致的 JSON 注入风险。
 *
 * <p><b>安全说明</b>：所有字段均为不可变，序列化时使用 JSON 库自动转义特殊字符，
 * 防止文件名包含双引号等字符破坏 JSON 结构。
 *
 * @param audit      审计标识（固定为 true）
 * @param operation  操作类型
 * @param fileNodeId 文件节点 ID
 * @param fileName   文件名
 * @param nodeType   节点类型
 * @param operatorId 操作人 ID
 * @param operatedAt 操作时间
 * @param storageKey 存储对象键
 * @param bucketName 存储桶名称
 * @param extra      额外参数
 * @param result     操作结果（success/fail）
 * @param eventId    事件唯一 ID
 * @author ydsz-team
 * @since 26.09.01
 */
public record AuditEvent(
    boolean audit,
    String operation,
    String fileNodeId,
    String fileName,
    String nodeType,
    String operatorId,
    LocalDateTime operatedAt,
    String storageKey,
    String bucketName,
    String extra,
    String result,
    String eventId
) implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 构造成功的审计事件。
   *
   * @param operation  操作类型
   * @param fileNodeId 文件节点 ID
   * @param fileName   文件名
   * @param nodeType   节点类型
   * @param operatorId 操作人 ID
   * @param operatedAt 操作时间
   * @param storageKey 存储对象键
   * @param bucketName 存储桶名称
   * @param extra      额外参数
   * @param eventId    事件唯一 ID
   * @return 审计事件实例
   */
  public static AuditEvent success(
      String operation,
      String fileNodeId,
      String fileName,
      String nodeType,
      String operatorId,
      LocalDateTime operatedAt,
      String storageKey,
      String bucketName,
      String extra,
      String eventId) {
    return new AuditEvent(
        true,
        operation,
        fileNodeId,
        fileName,
        nodeType,
        operatorId,
        operatedAt,
        storageKey,
        bucketName,
        extra,
        "success",
        eventId);
  }

  /**
   * 构造失败的审计事件。
   *
   * @param operation  操作类型
   * @param fileNodeId 文件节点 ID
   * @param fileName   文件名
   * @param operatorId 操作人 ID
   * @param operatedAt 操作时间
   * @param extra      额外参数（可包含失败原因）
   * @param eventId    事件唯一 ID
   * @return 审计事件实例
   */
  public static AuditEvent fail(
      String operation,
      String fileNodeId,
      String fileName,
      String operatorId,
      LocalDateTime operatedAt,
      String extra,
      String eventId) {
    return new AuditEvent(
        true,
        operation,
        fileNodeId,
        fileName,
        null,
        operatorId,
        operatedAt,
        null,
        null,
        extra,
        "fail",
        eventId);
  }
}
