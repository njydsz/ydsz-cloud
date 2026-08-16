package com.njydsz.literule.server.spi;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;

/**
 * 规则动作分发器
 *
 * <p>聚合所有 {@link RuleActionHandler}，在规则触发后统一分发。
 * 由 {@code LiteRuleAutoConfiguration} 自动装配并注入到 {@code DefaultRuleEngine}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class RuleActionDispatcher {

    private final List<RuleActionHandler> handlers = new ArrayList<>();

    /**
     * 注册动作处理器
     *
     * @param handler 动作处理器
     */
    public void register(RuleActionHandler handler) {
        if (handler != null) {
            handlers.add(handler);
        }
    }

    /**
     * 分发触发结果到所有已注册的处理器
     *
     * @param triggered 触发的规则结果列表
     * @param context   规则上下文
     */
    public void dispatchActions(List<RuleResult> triggered, RuleContext context) {
        if (handlers.isEmpty() || triggered == null || triggered.isEmpty()) {
            return;
        }
        for (RuleActionHandler handler : handlers) {
            try {
                handler.handle(triggered, context);
            } catch (Exception e) {
                log.warn("[LiteRule-Action] 动作处理器执行异常: handler={}, error={}",
                        handler.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 获取已注册的处理器数量
     *
     * @return 处理器数量
     */
    public int size() {
        return handlers.size();
    }
}
