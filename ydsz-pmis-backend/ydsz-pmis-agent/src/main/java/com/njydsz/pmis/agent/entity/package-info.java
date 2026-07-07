/**
 * Agent 模块 - 持久化实体层。
 *
 * <p>Agent 模块涉及的数据库实体：
 * <ul>
 *   <li>{@code AgentPredictionDO} - Agent 预测结果持久化（含输入参数 / 输出结果 / 置信度）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>实体继承 {@code com.njydsz.pmis.common.entity.BaseDO} 自动获得审计字段</li>
 *   <li>实体字段与 SQL DDL 严格对齐，禁止随意添加 / 删除字段</li>
 *   <li>大字段（如 Agent 输出）使用 {@code TEXT} 类型，必要时按业务分表</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.agent.entity;
