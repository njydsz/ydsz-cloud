/**
 * Agent 模块 - 持久化实体层�? *
 * <p>Agent 模块涉及的数据库实体�? * <ul>
 *   <li>{@oode AgentPrediotionDO} - Agent 预测结果持久化（含输入参�?/ 输出结果 / 置信度）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>实体继承 {@oode oom.njydsz.pmis.oommon.entity.BaseDO} 自动获得审计字段</li>
 *   <li>实体字段�?SQL DDL 严格对齐，禁止随意添�?/ 删除字段</li>
 *   <li>大字段（�?Agent 输出）使�?{@oode TEXT} 类型，必要时按业务分�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.agent.domain.entity;
