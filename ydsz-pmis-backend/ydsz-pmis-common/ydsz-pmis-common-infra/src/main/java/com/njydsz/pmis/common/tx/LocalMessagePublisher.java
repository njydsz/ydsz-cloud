package com.njydsz.pmis.common.tx;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 本地消息表发布器（P0-4 分布式事务：本地消息表模式）
 *
 * <p>在业务事务内调用 {@link #publish} 将消息写入本地消息表，
 * 事务提交后由 {@link TransactionPostProcessor} 触发异步投递。
 *
 * <h3>使用示例</h3>
 * <pre>
 * &#64;Transactional
 * public void createProject(ProjectCreateDTO dto) {
 *     projectMapper.insert(project);
 *     // 在同一事务内写入消息表，保证原子性
 *     localMessagePublisher.publish("NOTIFICATION", "ydsz-pmis-message",
 *         "/message/send", messageRequestJson);
 * }
 * </pre>
 *
 * <h3>投递流程</h3>
 * <ol>
 *   <li>事务提交 → afterCommit 回调触发投递</li>
 *   <li>投递成功 → 标记 DONE</li>
 *   <li>投递失败 → retryCount++，计算下次重试时间（指数退避）</li>
 *   <li>达到最大重试次数 → 标记 DEAD，等待人工处理</li>
 * </ol>
 *
 * <p>注意：本类提供发布和投递框架。实际投递需配置 {@link MessageDispatcher} 实现具体的 Feign/MQ 调用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalMessagePublisher {

    private final TransactionPostProcessor txPostProcessor;
    private final LocalMessageRepository messageRepository;

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRIES = 5;

    /**
     * 在当前事务内发布消息到本地消息表。
     *
     * <p>消息在事务提交后异步投递。如果事务回滚，消息不会被投递。
     *
     * @param messageType    消息类型
     * @param targetService  目标服务名
     * @param targetEndpoint 目标接口路径
     * @param payload        消息体 JSON
     */
    public void publish(String messageType, String targetService, String targetEndpoint, String payload) {
        publish(messageType, targetService, targetEndpoint, payload, DEFAULT_MAX_RETRIES);
    }

    /**
     * 在当前事务内发布消息到本地消息表（可配置最大重试次数）。
     *
     * @param messageType    消息类型
     * @param targetService  目标服务名
     * @param targetEndpoint 目标接口路径
     * @param payload        消息体 JSON
     * @param maxRetries     最大重试次数
     */
    public void publish(String messageType, String targetService, String targetEndpoint,
                        String payload, int maxRetries) {
        LocalMessageDO message = new LocalMessageDO();
        message.setMessageId(UUID.randomUUID().toString());
        message.setMessageType(messageType);
        message.setTargetService(targetService);
        message.setTargetEndpoint(targetEndpoint);
        message.setPayload(payload);
        message.setStatus("PENDING");
        message.setRetryCount(0);
        message.setMaxRetries(maxRetries);
        message.setNextRetryAt(LocalDateTime.now());
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());

        // 在事务内持久化消息（与业务数据在同一事务中提交）
        messageRepository.save(message);

        // 注册事务提交后回调，异步投递消息
        txPostProcessor.executeAfterCommit(() -> {
            try {
                dispatchMessage(message);
            } catch (Exception e) {
                log.error("[LocalMessage] 消息投递失败 messageId={} type={} reason={}",
                        message.getMessageId(), messageType, e.getMessage(), e);
                messageRepository.markFailed(message.getMessageId(), e.getMessage());
            }
        });
    }

    /**
     * 投递消息（由子类或配置的 dispatcher 实现）
     *
     * @param message 本地消息
     */
    private void dispatchMessage(LocalMessageDO message) {
        MessageDispatcher dispatcher = messageRepository.getDispatcher();
        if (dispatcher == null) {
            log.warn("[LocalMessage] 未配置 MessageDispatcher，消息仅入库不投递 messageId={}",
                    message.getMessageId());
            return;
        }
        boolean success = dispatcher.dispatch(message);
        if (success) {
            messageRepository.markDone(message.getMessageId());
            log.info("[LocalMessage] 消息投递成功 messageId={} type={}",
                    message.getMessageId(), message.getMessageType());
        } else {
            handleRetry(message, "Dispatcher returned false");
        }
    }

    /**
     * 处理重试逻辑（指数退避）
     *
     * @param message 消息
     * @param error   错误信息
     */
    private void handleRetry(LocalMessageDO message, String error) {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount() + 1;
        if (retryCount >= message.getMaxRetries()) {
            messageRepository.markDead(message.getMessageId(), error);
            log.error("[LocalMessage] 消息达到最大重试次数，标记为 DEAD messageId={} retries={}",
                    message.getMessageId(), retryCount);
        } else {
            // 指数退避：2^retryCount 秒
            long delaySeconds = (long) Math.pow(2, retryCount);
            LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delaySeconds);
            messageRepository.markFailedWithRetry(message.getMessageId(), retryCount, nextRetry, error);
            log.warn("[LocalMessage] 消息投递失败，安排重试 messageId={} retry={} nextRetryAt={}s",
                    message.getMessageId(), retryCount, delaySeconds);
        }
    }

    /**
     * 消息投递器接口
     *
     * <p>由具体实现类提供 Feign 调用或 MQ 发送逻辑。
     */
    @FunctionalInterface
    public interface MessageDispatcher {
        /**
         * 投递消息
         *
         * @param message 本地消息
         * @return true 表示投递成功，false 表示投递失败需重试
         */
        boolean dispatch(LocalMessageDO message);
    }

    /**
     * 本地消息表仓储接口
     *
     * <p>由各模块实现具体的持久化逻辑（MyBatis-Plus / JPA 等）。
     */
    public interface LocalMessageRepository {
        void save(LocalMessageDO message);
        void markDone(String messageId);
        void markFailed(String messageId, String error);
        void markFailedWithRetry(String messageId, int retryCount, LocalDateTime nextRetryAt, String error);
        void markDead(String messageId, String error);
        MessageDispatcher getDispatcher();
    }
}
