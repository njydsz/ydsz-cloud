package com.njydsz.message.server.service.chain;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;

/**
 * 消息发送管线编排引擎。
 *
 * <p>负责按 order 排序并依次执行所有 {@link SendHandler}。
 * 任一 Handler 返回 false 时管线短路，后续 Handler 不再执行。
 *
 * <p>扩展方式：实现 {@link SendHandler} 接口并注册为 Spring Bean，
 * Spring 容器启动时自动注入。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class SendPipeline implements InitializingBean {

    /** 按管线分组的 Handler 列表 */
    private final List<SendHandler> handlers;

    private static final Comparator<SendHandler> ORDER_COMPARATOR =
            Comparator.comparingInt(SendHandler::order);

    /**
     * Spring 自动注入所有 {@link SendHandler} 实现。
     *
     * @param handlerList Spring 容器中所有 SendHandler Bean
     */
    public SendPipeline(List<SendHandler> handlerList) {
        this.handlers = new CopyOnWriteArrayList<>(handlerList);
        this.handlers.sort(ORDER_COMPARATOR);
    }

    /**
     * 执行管线预处理，并返回填充完毕的上下文。
     *
     * <p>按 order 依次调用各 Handler，任一 Handler 设置 errorResult 后终止。
     * 全部通过时返回填充完毕的 SendContext。
     *
     * @param request 原始消息请求
     * @param ctx     调用方创建的上下文实例
     * @return 管线执行后的上下文（ctx.errorResult != null 表示失败）
     */
    public SendContext execute(MessageRequest request, SendContext ctx) {
        for (SendHandler handler : handlers) {
            try {
                boolean passed = handler.handle(request, ctx);
                if (!passed) {
                    log.debug("[Pipeline] Handler 短路: handler={}", handler.name());
                    return ctx;
                }
            } catch (Exception e) {
                log.error("[Pipeline] Handler 执行异常: handler={} err={}",
                        handler.name(), e.getMessage(), e);
                ctx.setErrorResult(MessageResult.fail(ctx.getChannel(),
                        "管线处理异常 [" + handler.name() + "]: " + e.getMessage()));
                return ctx;
            }
        }
        return ctx;
    }

    @Override
    public void afterPropertiesSet() {
        log.info("[Pipeline] 管线初始化完成: handlerCount={} handlers={}",
                handlers.size(),
                handlers.stream().map(h -> h.name() + "(" + h.order() + ")")
                        .reduce((a, b) -> a + " -> " + b).orElse(""));
    }

    /**
     * 动态注册 Handler（运行时扩展）。
     *
     * @param handler 新增处理器
     */
    public void registerHandler(SendHandler handler) {
        handlers.add(handler);
        handlers.sort(ORDER_COMPARATOR);
        log.info("[Pipeline] 动态注册 Handler: {} order={}", handler.name(), handler.order());
    }

    /**
     * 运行时移除 Handler。
     *
     * @param handlerName Handler 名称
     * @return 是否移除成功
     */
    public boolean removeHandler(String handlerName) {
        boolean removed = handlers.removeIf(h -> h.name().equals(handlerName));
        if (removed) {
            log.info("[Pipeline] 移除 Handler: {}", handlerName);
        }
        return removed;
    }

    /**
     * 获取当前管线中的 Handler 列表（不可修改）。
     *
     * @return Handler 列表副本
     */
    public List<SendHandler> getHandlers() {
        return List.copyOf(handlers);
    }
}
