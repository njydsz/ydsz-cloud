package com.njydsz.pmis.workflow.infra.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.FlowQuickCommentDO;

/**
 * 审批常用语 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Mapper
public interface FlowQuickCommentMapper extends BaseMapper<FlowQuickCommentDO> {
}
