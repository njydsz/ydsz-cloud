package com.njydsz.pmis.common.notify.email.listener;

import com.njydsz.pmis.common.notify.email.domain.Email;
import com.njydsz.pmis.common.notify.email.domain.SendResult;

/**
 * 邮件发送监听器接口
 *
 * <p>提供邮件发送过程的关键节点回调，支持在发送前验证、发送后记录等场景。
 * 可通过 {@link com.njydsz.pmis.common.notify.email.service.EmailService#registerListener} 注册多个监听器。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * EmailService emailService;
 * emailService.registerListener(new EmailSendListener() {
 *     @Override
 *     public void onBeforeSend(Email email) {
 *         // 发送前记录日志
 *         log.info("准备发送邮件至: {}", email.getTo());
 *     }
 *
 *     @Override
 *     public void onSuccess(Email email, SendResult result) {
 *         // 发送成功后更新任务状态
 *         taskService.updateStatus(result.getMessageId());
 *     }
 *
 *     @Override
 *     public void onFailure(Email email, Throwable exception) {
 *         // 发送失败后重试或告警
 *         alertService.send("邮件发送失败: " + exception.getMessage());
 *     }
 * });
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 1.0.0
 */
public interface EmailSendListener {

    /**
     * 发送前回调
     *
     * <p>在邮件发送之前调用，可用于：
     * <ul>
     *   <li>日志记录</li>
     *   <li>发送前数据校验</li>
     *   <li>修改邮件内容</li>
     * </ul>
     *
     * @param email 待发送的邮件对象
     */
    default void onBeforeSend(Email email) {
    }

    /**
     * 发送成功回调
     *
     * <p>在邮件发送成功之后调用，可用于：
     * <ul>
     *   <li>日志记录</li>
     *   <li>更新任务状态</li>
     *   <li>发送确认通知</li>
     * </ul>
     *
     * @param email 已发送的邮件对象
     * @param result 发送结果
     */
    default void onSuccess(Email email, SendResult result) {
    }

    /**
     * 发送失败回调
     *
     * <p>在邮件发送失败之后调用，可用于：
     * <ul>
     *   <li>日志记录</li>
     *   <li>触发重试机制</li>
     *   <li>发送告警通知</li>
     * </ul>
     *
     * @param email 发送失败的邮件对象
     * @param exception 失败原因
     */
    default void onFailure(Email email, Throwable exception) {
    }

    /**
     * 获取监听器执行顺序
     *
     * <p>数值越小越先执行，默认值为 0</p>
     *
     * @return 监听器顺序
     */
    default int getOrder() {
        return 0;
    }
}