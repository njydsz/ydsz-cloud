package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 规则动作分发器（P1-1 规则与消息通知联动）
 *
 * <p>管理所有 {@link RuleActionHandler} 的注册/注销，并在规则触发后统一分发。
 * 引擎在评估完成后调用 {@link #dispatchActions}，将触发结果传递给所有已注册的 handler。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>线程安全：使用 {@link CopyOnWriteArrayList}，支持运行时动态注册/注销</li>
 *   <li>异步执行：{@link RuleActionHandler#isAsync()} 为 true 的 handler 在独立线程执行</li>
 *   <li>异常隔离：单个 handler 异常不影响其他 handler 和评估主流程</li>
 *   <li>优先级排序：按 {@link RuleActionHandler#getOrder()} 排序执行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
@Slf4j
public class RuleActionDispatcher {

    private final CopyOnWriteArrayList<RuleActionHandler> handlers = new CopyOnWriteArrayList<>();

    /**
     * 注册 ActionHandler
     *
     * @param handler 处理器；null 忽略
     */
    public void register(RuleActionHandler handler) {
        if (handler == null) {
            return;
        }
        unregister(handler.getHandlerId());
        handlers.add(handler);
        log.info("[LiteRule-Action] 注册 RuleActionHandler: handlerId={}, async={}, order={}",
                handler.getHandlerId(), handler.isAsync(), handler.getOrder());
    }

    /**
     * 注销指定 handlerId 的 handler
     */
    public void unregister(String handlerId) {
        if (handlerId == null) {
            return;
        }
        handlers.removeIf(h -> handlerId.equals(h.getHandlerId()));
    }

    /**
     * 是否已注册任何 handler
     */
    public boolean hasHandlers() {
        return !handlers.isEmpty();
    }

    /**
     * 分发规则触发动作
     *
     * <p>按优先级排序后依次调用所有 handler：
     * <ul>
     *   <li>异步 handler：通过 {@link CompletableFuture#runAsync} 在默认 ForkJoinPool 中执行</li>
     *   <li>同步 handler：在当前线程中执行</li>
     *   <li>异常隔离：try-catch 包裹每个 handler，异常仅记录 WARN 日志</li>
     * </ul>
     *
     * @param results 已触发的规则结果列表
     * @param context 规则评估上下文
     */
    public void dispatchActions(List<RuleResult> results, RuleContext context) {
        if (results == null || results.isEmpty() || handlers.isEmpty()) {
            return;
        }
        // 过滤出已触发的结果
        List<RuleResult> triggered = results.stream()
                .filter(RuleResult::isTriggered)
                .collect(Collectors.toList());
        if (triggered.isEmpty()) {
            return;
        }

        // 按 order 排序
        List<RuleActionHandler> sorted = handlers.stream()
                .sorted(Comparator.comparingInt(RuleActionHandler::getOrder))
                .toList();

        for (RuleActionHandler handler : sorted) {
            if (handler.isAsync()) {
                CompletableFuture.runAsync(() -> safeInvoke(handler, triggered, context))
                        .exceptionally(ex -> {
                            log.warn("[LiteRule-Action] 异步 handler {} 执行异常: {}",
                                    handler.getHandlerId(), ex.getMessage());
                            return null;
                        });
            } else {
                safeInvoke(handler, triggered, context);
            }
        }
    }

    /**
     * 安全调用单个 handler（异常隔离）
     */
    private void safeInvoke(RuleActionHandler handler, List<RuleResult> results, RuleContext context) {
        try {
            handler.onTriggered(results, context);
        } catch (Exception e) {
            log.warn("[LiteRule-Action] Handler {} 执行失败: {}",
                    handler.getHandlerId(), e.getMessage(), e);
        }
    }

    /**
     * 获取已注册的 handler 数量
     */
    public int size() {
        return handlers.size();
    }
}
