package com.njydsz.common.event.admin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * Outbox 运维管理 HTTP 接口骨架（F-2）
 *
 * <p>提供 Outbox 消息队列的运维操作能力：
 *
 * <ul>
 *   <li>分页查询死信消息（按时间 / 事件类型过滤）
 *   <li>手动重试死信消息（CAS 重置为 PENDING）
 *   <li>批量重试死信消息
 *   <li>安全删除已终态消息
 *   <li>实时队列深度统计
 *   <li>手动触发历史消息清理
 * </ul>
 *
 * <p><b>设计原则：</b>本骨架不引入 spring-web 相关依赖（ ResponseEntity / @RestController 等），
 * 确保 event 模块不强制依赖 Web 容器。
 *
 * <p>业务 Web 模块（如 ydsz-system）可按以下方式装配：
 *
 * <pre>{@code
 * // 业务 web 模块中声明真正的 REST 适配器
 * &#64;RestController
 * &#64;RequestMapping("/admin/events/outbox")
 * &#64;RequiredArgsConstructor
 * public class OutboxAdminRestController {
 *     private final OutboxAdminController adminController;
 *
 *     &#64;GetMapping("/dead-letters")
 *     public YdszResponse&lt;Page&lt;OutboxMessage&gt;&gt; listDeadLetters(
 *             &#64;RequestParam(defaultValue = "0") int page,
 *             &#64;RequestParam(defaultValue = "20") int size,
 *             &#64;RequestParam(required = false) String eventType) {
 *         return YdszResponse.success(adminController.listDeadLetters(page, size, eventType));
 *     }
 *
 *     &#64;PostMapping("/dead-letters/{id}/retry")
 *     public YdszResponse&lt;Boolean&gt; retryDeadLetter(&#64;PathVariable String id) {
 *         return YdszResponse.success(adminController.retryDeadLetter(id));
 *     }
 *
 *     &#64;GetMapping("/statistics")
 *     public YdszResponse&lt;Map&lt;String, Long&gt;&gt; getStatistics() {
 *         return YdszResponse.success(adminController.getStatistics());
 *     }
 * }
 * }</pre>
 *
 * <p><b>编码规范遵循：</b>YDIZ-API-001（版本 Header 协商，路径不含版本段）。
 *
 * <p><b>安全建议：</b>生产环境应通过网关鉴权限制管理员角色访问此接口。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class OutboxAdminController {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(OutboxAdminController.class);

  /** Outbox 运维管理服务 */
  private final OutboxAdminService outboxAdminService;

  /**
   * 构造函数
   *
   * @param outboxAdminService Outbox 运维管理服务
   */
  public OutboxAdminController(OutboxAdminService outboxAdminService) {
    this.outboxAdminService = outboxAdminService;
  }

  /**
   * 分页查询死信消息
   *
   * @param page 页码（从 0 开始）
   * @param size 每页大小（默认 20，最大 200）
   * @param eventType 事件类型过滤（可选）
   * @return 死信消息分页列表
   */
  public Page<OutboxMessage> listDeadLetters(int page, int size, String eventType) {
    return outboxAdminService.listDeadLetters(page, size, eventType);
  }

  /**
   * 手动重试单条死信消息
   *
   * @param messageId 消息 ID
   * @return true 表示重置成功，false 表示消息不存在或状态不是 DEAD_LETTER
   */
  public boolean retryDeadLetter(String messageId) {
    return outboxAdminService.retryDeadLetter(messageId);
  }

  /**
   * 批量重试所有死信消息
   *
   * @param eventType 事件类型过滤（可选）
   * @return 重置的消息数量
   */
  public int retryAllDeadLetters(String eventType) {
    return outboxAdminService.retryAllDeadLetters(eventType);
  }

  /**
   * 安全删除已终态消息
   *
   * <p>仅允许删除 SENT 或 DEAD_LETTER 状态的消息。
   *
   * @param messageId 消息 ID
   * @return true 表示删除成功
   */
  public boolean deleteMessage(String messageId) {
    return outboxAdminService.deleteTerminatedMessage(messageId);
  }

  /**
   * 获取队列深度实时统计
   *
   * @return 各状态消息数量
   */
  public Map<String, Long> getStatistics() {
    return outboxAdminService.getQueueStatistics();
  }

  /**
   * 手动触发历史消息清理
   *
   * @param retentionDays 保留天数（默认 7）
   * @return 删除的消息数量
   */
  public int cleanup(int retentionDays) {
    int days = retentionDays > 0 ? retentionDays : 7;
    return outboxAdminService.cleanupSentMessages(days);
  }

  /**
   * 日志记录器（供子类或切面使用）
   *
   * @return 日志实例
   */
  protected Logger logger() {
    return LOG;
  }
}
