package com.njydsz.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.workflow.domain.entity.FlowIdempotent;

/**
 * 工作流幂等记录 Mapper。
 *
 * <p>对应数据表 <code>ydsz_flow_idempotent</code>，提供幂等记录的 CRUD 能力。
 *
 * <p>幂等记录通过「scope + key_hash」组合唯一约束防重放，通过 ttl_at 自动清理过期记录。
 *
 * @author ydsz-team
 * @since 26.10.02
 * @see com.njydsz.workflow.domain.entity.FlowIdempotent
 */
@Mapper
public interface FlowIdempotentMapper extends BaseMapper<FlowIdempotent> {
}
