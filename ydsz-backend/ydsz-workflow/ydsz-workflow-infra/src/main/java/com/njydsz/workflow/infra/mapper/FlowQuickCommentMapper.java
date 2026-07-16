package com.njydsz.workflow.infra.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowQuickCommentDO;

/**
 * 审批常用语 Mapper
 *
 * @author ydsz-team
 * @since 1.8.0
 */
@Mapper
public interface FlowQuickCommentMapper extends BaseMapper<FlowQuickCommentDO> {
}
