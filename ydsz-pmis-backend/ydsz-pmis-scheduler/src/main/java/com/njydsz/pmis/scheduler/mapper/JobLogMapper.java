package com.njydsz.pmis.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.scheduler.entity.JobLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务日志 Mapper
 */
@Mapper
public interface JobLogMapper extends BaseMapper<JobLogDO> {
}
