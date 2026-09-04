package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.agent.domain.entity.AgentApproval;

/**
 * Agent 人工审批请求 Mapper
 *
 * <p>映射 {@code ydsz_agt_approval} 表，持久化 HITL 审批请求。 <b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id}
 * 过滤条件，本接口不感知。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface AgentApprovalMapper extends BaseMapper<AgentApproval> {}
