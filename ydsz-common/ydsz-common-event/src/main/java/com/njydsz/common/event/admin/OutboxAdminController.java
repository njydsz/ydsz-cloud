package com.njydsz.common.event.admin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * Outbox 运维管理 HTTP 接口（F-2）
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
 * <p><b>路径前缀：</b>{@code /admin/events/outbox}
 *
 * <p><b>编码规范遵循：</b>
 *
 * <ul>
 *   <li>YDIZ-API-001：API 版本通过 Header 协商，路径不包含版本段
 *   <li>YDIZ-API-002：@ApiVersion 注解位于 @RequestMapping 上方
 * </ul>
 *
 * <p><b>安全建议：</b>生产环境应通过网关鉴权限制管理员角色访问此接口。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
// 需注意：本 Controller 不强制引入 spring-web，通过以下方式之一启用：
// 1. 业务模块引入 spring-boot-starter-web 后，本类自动被组件扫描装配
// 2. 业务模块通过 @Import(OutboxAdminController.class) 显式装配
// 如业务模块使用 spring-webflux，请使用 OutboxAdminRouterFunction 替代
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

  // ==================== 注：以下方法签名仅供参考，实际装配方式二选一 ====================
  //
  // 选项 A：spring-web（Spring MVC）路径注册示例（需引入 spring-boot-starter-web）
  // @RestController
  // @RequestMapping("/admin/events/outbox")
  // @Tag(name = "Outbox Admin", description = "Outbox 消息队列运维管理")
  // public class OutboxAdminController implements OutboxAdminEndpoints { ... }
  //
  // 选项 B：spring-webflux 函数式注册示例（需引入 spring-boot-starter-webflux）
  // @Configuration
  // public class OutboxAdminRouter {
  //   @Bean
  //   public RouterFunction<ServerResponse> outboxAdminRoutes(OutboxAdminController controller) {
  //     return RouterFunctions.route()
  //         .path("/admin/events/outbox", builder -> builder
  //             .GET("/dead-letters", controller::listDeadLetters)
  //             .POST("/dead-letters/{id}/retry", controller::retryDeadLetter)
  //             .POST("/dead-letters/retry-all", controller::retryAllDeadLetters)
  //             .GET("/statistics", controller::getStatistics)
  //             .DELETE("/messages/{id}", controller::deleteMessage)
  //             .POST("/cleanup", controller::cleanup))
  //         .build();
  //   }
  // }
  //
  // 具体实现由业务模块根据实际 Web 框架选择，核心逻辑已封装在 OutboxAdminService 中。

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
   * @return 操作结果
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
   * @param messageId 消息 ID
   * @return 操作结果
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
   * 通用响应包装
   *
   * @param data 响应数据
   * @return 标准响应
   */
  protected <T> ResponseEntity<T> ok(T data) {
    return ResponseEntity.ok(data);
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
