package com.njydsz.agent.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.agent.domain.entity.DagWorkflow;

/**
 * DAG 工作流 Mapper。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Mapper
public interface DagWorkflowMapper extends BaseMapper<DagWorkflow> {
}
