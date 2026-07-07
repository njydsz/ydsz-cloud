package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度节点心跳 Mapper。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobNodeMapper extends BaseMapper<JobNodeDO> {
}
