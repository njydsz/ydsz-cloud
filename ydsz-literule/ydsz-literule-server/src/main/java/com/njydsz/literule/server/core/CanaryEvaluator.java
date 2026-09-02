package com.njydsz.literule.server.core;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;

/**
 * 灰度评估器
 *
 * <p>负责灰度路由和候选版本评估：
 *
 * <ul>
 *   <li>灰度路由决策：根据 canaryRatio 和上下文决定是否路由到候选版本
 *   <li>候选版本构建：根据灰度定义构建候选规则实例
 *   <li>灰度结果标记：标记结果为灰度版本，便于后续分析
 *   <li>分桶记录：记录灰度分桶决策，用于灰度效果分析
 * </ul>
 *
 * @since 1.4.0
 * @author ydsz-team
 */
@Slf4j
public class CanaryEvaluator {

    /** 灰度路由器 */
    private final RuleCanaryRouter canaryRouter;

    /** 是否启用灰度路由 */
    private volatile boolean canaryEnabled = true;

    /**
     * 构造灰度评估器
     *
     * @param canaryRouter 灰度路由器
     */
    public CanaryEvaluator(RuleCanaryRouter canaryRouter) {
        this.canaryRouter = canaryRouter;
    }

    /**
     * 解析规则对应的灰度候选定义
     *
     * <p>仅当以下条件全部满足时返回非 null：
     *
     * <ul>
     *   <li>canaryEnabled = true
     *   <li>canaryRouter 已注入
     *   <li>规则暴露了 RuleDefinitionDTO（即 {@code rule.getRuleDefinition()} 非空）
     *   <li>canaryRatio > 0 且配置了候选表达式（条件或严重度）
     * </ul>
     *
     * @param rule 规则
     * @return 灰度定义；不满足条件返回 null
     */
    public RuleDefinitionDTO resolveCanaryDefinition(Rule rule) {
        if (!canaryEnabled || canaryRouter == null) {
            return null;
        }
        RuleDefinitionDTO def = rule.getRuleDefinition();
        if (def == null || def.getCanaryRatio() <= 0) {
            return null;
        }
        if (def.getCanaryConditionExpression() == null && def.getCanarySeverityExpression() == null) {
            return null;
        }
        return def;
    }

    /**
     * 判断是否应路由到灰度版本
     *
     * @param canaryDef 灰度定义
     * @param context 评估上下文
     * @return true 表示路由到灰度版本
     */
    public boolean shouldRouteToCanary(RuleDefinitionDTO canaryDef, RuleContextVO context) {
        return canaryRouter.shouldRouteToCanary(canaryDef, context);
    }

    /**
     * 构建灰度候选规则
     *
     * @param canaryDef 灰度定义
     * @return 灰度候选规则
     */
    public Rule buildCanaryRule(RuleDefinitionDTO canaryDef) {
        return canaryRouter.buildCanaryRule(canaryDef);
    }

    /**
     * 标记结果为灰度版本
     *
     * @param result 评估结果
     */
    public void markCanary(RuleResultVO result) {
        canaryRouter.markCanary(result);
    }

    /**
     * 记录灰度分桶决策
     *
     * @param ruleCode 规则编码
     * @param goCanary 是否路由到灰度
     */
    public void recordBucket(String ruleCode, boolean goCanary) {
        canaryRouter.recordBucket(ruleCode, goCanary);
    }

    /**
     * 设置是否启用灰度路由
     *
     * @param canaryEnabled 是否启用
     */
    public void setCanaryEnabled(boolean canaryEnabled) {
        this.canaryEnabled = canaryEnabled;
    }

    /**
     * 获取是否启用灰度路由
     *
     * @return 是否启用
     */
    public boolean isCanaryEnabled() {
        return canaryEnabled;
    }

    /**
     * 获取灰度路由器
     *
     * @return 灰度路由器
     */
    public RuleCanaryRouter getCanaryRouter() {
        return canaryRouter;
    }
}
