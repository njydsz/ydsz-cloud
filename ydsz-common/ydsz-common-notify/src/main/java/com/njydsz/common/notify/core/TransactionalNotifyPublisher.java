package com.njydsz.common.notify.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.njydsz.common.notify.enums.NotifyChannel;

/**
 * 事务安全通知发布器（P0-1）
 *
 * <p>确保通知发送与业务操作的原子性：业务事务提交后才触发通知发送， 避免业务回滚但通知已发出导致的数据不一致问题。
 *
 * <p><b>工作机制：</b>
 *
 * <ol>
 *   <li>业务代码调用 {@link #publishAfterCommit} 发布通知事件
 *   <li>事件被暂存，不立即发送
 *   <li>当外层事务成功提交后，{@link TransactionalEventListener} 回调触发
 *   <li>回调中调用 {@link AsyncNotifyService} 异步发送通知
 * </ol>
 *
 * <p>如果事务回滚，通知事件被丢弃，不会发送。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     private final TransactionalNotifyPublisher notifyPublisher;
 *
 *     @Transactional
 *     public void shipOrder(Long orderId) {
 *         orderRepository.updateStatus(orderId, "SHIPPED");
 *         // 事务提交后自动发送通知
 *         notifyPublisher.publishAfterCommit(
 *             NotifyChannel.EMAIL,
 *             customer.getEmail(),
 *             "订单已发货",
 *             "您的订单 " + orderId + " 已发货"
 *         );
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class TransactionalNotifyPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionalNotifyPublisher.class);

  private final ApplicationEventPublisher eventPublisher;
  private final AsyncNotifyService asyncNotifyService;

  /**
   * 构造事务安全通知发布器
   *
   * @param eventPublisher Spring 事件发布器
   * @param asyncNotifyService 异步通知服务
   */
  public TransactionalNotifyPublisher(
      ApplicationEventPublisher eventPublisher, AsyncNotifyService asyncNotifyService) {
    this.eventPublisher = eventPublisher;
    this.asyncNotifyService = asyncNotifyService;
  }

  /**
   * 在事务提交后发送通知
   *
   * <p>如果当前不存在事务，立即发送通知。 如果事务回滚，通知不会发送。
   *
   * @param channel 通知渠道
   * @param receiver 接收者
   * @param title 标题
   * @param content 内容
   */
  public void publishAfterCommit(
      NotifyChannel channel, String receiver, String title, String content) {
    NotifyRequest request = NotifyRequest.of(channel, receiver, title, content).build();
    publishAfterCommit(request);
  }

  /**
   * 在事务提交后发送模板通知
   *
   * @param channel 通知渠道
   * @param receiver 接收者
   * @param templateCode 模板编码
   * @param templateParams 模板参数
   */
  public void publishAfterCommit(
      NotifyChannel channel, String receiver, String templateCode, Object templateParams) {
    NotifyRequest request =
        NotifyRequest.of(channel, receiver, templateCode, null)
            .template(templateCode, templateParams)
            .build();
    publishAfterCommit(request);
  }

  /**
   * 在事务提交后发送通知（完整请求）
   *
   * @param request 通知请求
   */
  public void publishAfterCommit(NotifyRequest request) {
    if (request == null) {
      return;
    }
    LOG.debug(
        "[TransactionalNotifyPublisher] 发布事务后通知事件: channel={}, receiver={}",
        request.getChannel().getName(),
        request.getReceiver());
    eventPublisher.publishEvent(new NotifyAfterCommitEvent(request));
  }

  /**
   * 事务提交后事件监听
   *
   * <p>仅当外层事务成功提交后才触发通知发送。 使用 {@link TransactionPhase#AFTER_COMMIT} 确保事务一致性。
   *
   * @param event 通知事件
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onAfterCommit(NotifyAfterCommitEvent event) {
    NotifyRequest request = event.getRequest();
    LOG.info(
        "[TransactionalNotifyPublisher] 事务已提交，开始发送通知: channel={}, receiver={}",
        request.getChannel().getName(),
        request.getReceiver());

    try {
      if (request.isTemplateRequest()) {
        asyncNotifyService.sendAsync(request);
      } else {
        asyncNotifyService.sendAsync(
            request.getChannel(),
            request.getReceiver(),
            request.getTitle() != null ? request.getTitle() : "",
            request.getContent() != null ? request.getContent() : "");
      }
    } catch (Exception e) {
      LOG.error(
          "[TransactionalNotifyPublisher] 事务后通知发送异常: channel={}, receiver={}, error={}",
          request.getChannel().getName(),
          request.getReceiver(),
          e.getMessage(),
          e);
    }
  }

  /** 事务后通知事件 */
  public static class NotifyAfterCommitEvent {

    private final NotifyRequest request;

    public NotifyAfterCommitEvent(NotifyRequest request) {
      this.request = request;
    }

    public NotifyRequest getRequest() {
      return request;
    }
  }
}
