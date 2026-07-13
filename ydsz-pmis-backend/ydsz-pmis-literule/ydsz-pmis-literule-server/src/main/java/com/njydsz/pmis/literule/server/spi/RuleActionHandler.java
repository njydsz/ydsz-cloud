package com.njydsz.pmis.literule.server.spi;

import java.util.List;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;

/**
 * 规则动作处理器 SPI
 *
 * <p>规则触发后执行的后续动作（消息通知、工作流触发、定时任务触发等）。
 * 实现类通过 {@link RuleActionDispatcher} 注册，由 {@code DefaultRuleEngine} 在评估完成后调用。
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
@FunctionalInterface
public interface RuleActionHandler {

    /**
     * 处理规则触发结果
     *
     * @param triggered 触发的规则结果列表
     * @param context   规则上下文
     */
    void handle(List<RuleResult> triggered, RuleContext context);
}