package com.njydsz.pmis.workflow.mapper.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.notification.FlowQuickCommentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批常用语 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Mapper
public interface FlowQuickCommentMapper extends BaseMapper<FlowQuickCommentDO> {
}
