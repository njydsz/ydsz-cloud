package com.njydsz.pmis.agent.infra.mapper.hitl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.domain.entity.hitl.HitlApprovalRequestDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * HITL 审批请求 Mapper（P3-4 落地）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@Mapper
public interface HitlApprovalRequestMapper extends BaseMapper<HitlApprovalRequestDO> {
}
