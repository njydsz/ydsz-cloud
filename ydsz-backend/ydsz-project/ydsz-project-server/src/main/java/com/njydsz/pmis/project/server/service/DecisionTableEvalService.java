package com.njydsz.project.server.service;

import java.util.List;
import java.util.Map;

import com.njydsz.literule.server.spi.DecisionTableEvalProvider;

/**
 * DMN 决策表评估服务
 *
 * <p>按决策表编码加载并评估决策表，返回命中行的动作值列表。
 * 供规则引擎、工作流路由等场景调用。
 *
 * <p>继承 {@link DecisionTableEvalProvider} SPI，供 literule 模块的 Controller 反转依赖调用。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public interface DecisionTableEvalService extends DecisionTableEvalProvider {

    /**
     * 评估决策表
     *
     * @param tableCode 决策表编码
     * @param facts     事实数据（变量名 -> 值）
     * @return 命中行的动作值列表；无匹配时返回默认动作或空列表
     */
    List<Map<String, Object>> evaluate(String tableCode, Map<String, Object> facts);

    /**
     * 评估决策表（指定租户）
     *
     * <p>多租户场景下按租户隔离查询决策表。当前实体未启用租户隔离时，租户参数将被忽略。
     *
     * @param tableCode 决策表编码
     * @param facts     事实数据（变量名 -> 值）
     * @param tenantId  租户 ID（可空）
     * @return 命中行的动作值列表；无匹配时返回默认动作或空列表
     */
    List<Map<String, Object>> evaluate(String tableCode, Map<String, Object> facts, String tenantId);
}
