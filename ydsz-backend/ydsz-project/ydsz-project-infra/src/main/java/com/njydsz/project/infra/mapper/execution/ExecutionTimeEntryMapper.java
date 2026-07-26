package com.njydsz.project.infra.mapper.execution;

import com.njydsz.project.domain.entity.execution.ExecutionTimeEntryDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ExecutionTimeEntry Mapper。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Mapper
public interface ExecutionTimeEntryMapper extends BaseMapper<ExecutionTimeEntryDO> {
}
