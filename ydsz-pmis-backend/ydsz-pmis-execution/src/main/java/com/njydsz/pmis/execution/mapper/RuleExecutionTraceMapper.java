package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.RuleExecutionTraceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 规则执行链路追踪 Mapper
 *
 * @author ydsz-pmis
 * @since 2026-07-02
 */
@Mapper
public interface RuleExecutionTraceMapper extends BaseMapper<RuleExecutionTraceDO> {
}