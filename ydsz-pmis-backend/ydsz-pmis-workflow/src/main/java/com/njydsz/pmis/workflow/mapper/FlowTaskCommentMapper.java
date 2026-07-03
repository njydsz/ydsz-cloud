package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowTaskCommentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务评论 Mapper
 *
 * <p>对应 pmis_flow_task_comment 表，存储工作流任务下的独立沟通评论。
 * 当前仅依赖 MyBatis-Plus {@link BaseMapper} 通用方法，无需自定义 XML。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowTaskCommentMapper extends BaseMapper<FlowTaskCommentDO> {
}
