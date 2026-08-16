package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.agent.domain.entity.TokenUsageRecordDO;

/**
 * Token 用量记录 Mapper
 *
 * <p>对应数据表 {@code ydsz_agent_token_usage}。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>idx_conversation_id — 对话 ID 索引（按会话查询用量）</li>
 *   <li>idx_created_at — 创建时间索引（按时间范围聚合统计）</li>
 *   <li>idx_model_name — 模型名称索引（按模型分组统计）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>用量记录不做逻辑删除，永久保留以供审计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TokenUsageRecordMapper extends BaseMapper<TokenUsageRecordDO> {
}
