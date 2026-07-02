package com.njydsz.pmis.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.scheduler.entity.JobDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务定义 Mapper
 *
 * <p>对应 pmis_job 表，提供按 jobKey 查询、启动加载 NORMAL 任务、统计字段更新。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobMapper extends BaseMapper<JobDO> {

    /**
     * 根据 jobKey 查询
     */
    JobDO selectByJobKey(@Param("jobKey") String jobKey);

    /**
     * 查询所有 NORMAL 状态任务（启动时加载）
     */
    List<JobDO> selectAllNormal();

    /**
     * 更新任务统计字段
     */
    int updateStats(@Param("id") Long id,
                    @Param("lastFireTime") java.time.LocalDateTime lastFireTime,
                    @Param("nextFireTime") java.time.LocalDateTime nextFireTime,
                    @Param("fireCount") Long fireCount,
                    @Param("successCount") Long successCount,
                    @Param("failCount") Long failCount,
                    @Param("status") String status);
}
