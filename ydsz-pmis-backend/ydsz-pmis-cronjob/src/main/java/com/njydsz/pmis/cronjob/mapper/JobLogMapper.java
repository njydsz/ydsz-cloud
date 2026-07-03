package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务日志 Mapper
 *
 * <p>对应 pmis_job_log 表，归档每次任务执行的开始/结束/耗时/状态/结果，供执行历史查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobLogMapper extends BaseMapper<JobLogDO> {
}
