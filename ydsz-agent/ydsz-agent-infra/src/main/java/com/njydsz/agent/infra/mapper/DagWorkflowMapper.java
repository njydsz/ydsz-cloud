package com.njydsz.agent.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.agent.infra.entity.DagWorkflowPO;

/**
 * DAG 工作流 Mapper。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Mapper
public interface DagWorkflowMapper extends BaseMapper<DagWorkflowPO> {
}
