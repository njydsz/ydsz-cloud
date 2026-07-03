package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobDO;
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
     *
     * @param jobKey 任务 KEY
     * @return 任务定义，不存在时返回 null
     */
    JobDO selectByJobKey(@Param("jobKey") String jobKey);

    /**
     * 查询所有 NORMAL 状态任务（启动时加载）
     *
     * @return NORMAL 状态任务列表
     */
    List<JobDO> selectAllNormal();

    /**
     * 更新任务统计字段
     *
     * @param id           任务 ID
     * @param lastFireTime 上次触发时间
     * @param nextFireTime 下次触发时间
     * @param fireCount    触发次数
     * @param successCount 成功次数
     * @param failCount    失败次数
     * @param status       任务状态（失败时设为 ERROR，成功时传 null 不更新）
     * @return 受影响行数
     */
    int updateStats(@Param("id") Long id,
                    @Param("lastFireTime") java.time.LocalDateTime lastFireTime,
                    @Param("nextFireTime") java.time.LocalDateTime nextFireTime,
                    @Param("fireCount") Long fireCount,
                    @Param("successCount") Long successCount,
                    @Param("failCount") Long failCount,
                    @Param("status") String status);
}
