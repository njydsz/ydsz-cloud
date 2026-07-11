package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;

import java.util.List;

/**
 * 规则动作处理器 SPI（P1-1 规则与消息通知联动）
 *
 * <p>当规则触发时，引擎会调用所有已注册的 {@code RuleActionHandler} 执行后续动作，
 * 如发送消息通知、触发工作流、记录审计日志等。
 *
 * <h3>典型实现</h3>
 * <ul>
 *   <li>{@code DefaultAlertActionHandler} — 发布 {@code UnifiedAlertEvent}，由 common 模块的
 *       {@code UnifiedAlertDispatcher} 消费并通过 message 模块发送通知</li>
 *   <li>消费方可自定义实现，如触发工作流、调用外部 API、写入数据仓库等</li>
 * </ul>
 *
 * <h3>执行时机</h3>
 * <ul>
 *   <li>在 {@code RuleEngine.evaluate} 返回前、所有规则评估完成后</li>
 *   <li>仅对 {@code triggered=true} 的结果调用</li>
 *   <li>默认异步执行（不阻塞评估主流程），可通过 {@link #isAsync()} 控制</li>
 * </ul>
 *
 * <h3>异常处理</h3>
 * <ul>
 *   <li>单个 handler 异常不影响其他 handler</li>
 *   <li>异常仅记录 WARN 日志，不向上传播</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
public interface RuleActionHandler {

    /**
     * 处理规则触发动作
     *
     * @param results 已触发的规则结果列表（非空）
     * @param context 规则评估上下文（含 facts、scenario 等）
     */
    void onTriggered(List<RuleResult> results, RuleContext context);

    /**
     * 处理器标识（如 "alert-notification"、"workflow-trigger"）
     *
     * @return 处理器标识
     */
    String getHandlerId();

    /**
     * 是否异步执行（默认 true）
     *
     * <p>true：在独立线程池中执行，不阻塞评估主流程；
     * false：同步执行，适用于需要强一致的场景（如审计日志必须在事务内写入）。
     *
     * @return true=异步执行
     */
    default boolean isAsync() {
        return true;
    }

    /**
     * 执行优先级（默认 0，数字小的先执行）
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }
}
