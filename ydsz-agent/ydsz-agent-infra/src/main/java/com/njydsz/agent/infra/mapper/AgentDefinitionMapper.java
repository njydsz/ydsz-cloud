package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.njydsz.agent.infra.entity.AgentDefinitionDO;

/**
 * Agent 定义 Mapper
 *
 * <p>对应数据表 <code>ydsz_agent_def</code>。
 *
 * <p>Agent 是可调用的 AI 智能体（对话/任务型），由 LLM + Tools + Prompt 组成，按业务场景定义。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_agent_code — Agent 编码唯一索引
 *   <li>idx_status — 状态过滤索引（DRAFT/PUBLISHED/DEPRECATED）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.agent.infra.entity.AgentDefinitionDO Agent 定义实体
 * @see com.njydsz.agent.server.service.AgentDefinitionService Agent Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
public interface AgentDefinitionMapper extends BaseMapper<AgentDefinitionDO> {}
