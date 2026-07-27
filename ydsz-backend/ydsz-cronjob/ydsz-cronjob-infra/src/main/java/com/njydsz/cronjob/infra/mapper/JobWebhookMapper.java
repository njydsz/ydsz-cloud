package com.njydsz.cronjob.infra.mapper.job;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.cronjob.domain.entity.job.JobWebhook;

/**
 * WebHook 订阅 Mapper（P3-13）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface JobWebhookMapper extends BaseMapper<JobWebhook> {

    /**
     * 查询指定事件类型的活跃 WebHook 列表。
     */
    @Select("SELECT id, name, event_type, job_key, job_group, callback_url, http_method, "
            + "       headers, secret, status, created_at, updated_at, deleted "
            + "FROM ydsz_job_webhook "
            + "WHERE event_type = #{eventType} AND status = 'ACTIVE' AND deleted = 0")
    List<JobWebhook> selectActiveByEventType(@Param("eventType") String eventType);

    /**
     * 查询指定事件类型且匹配 jobKey 的活跃 WebHook。
     */
    @Select("SELECT id, name, event_type, job_key, job_group, callback_url, http_method, "
            + "       headers, secret, status, created_at, updated_at, deleted "
            + "FROM ydsz_job_webhook "
            + "WHERE event_type = #{eventType} AND status = 'ACTIVE' AND deleted = 0 "
            + "  AND (job_key = #{jobKey} OR job_key IS NULL)")
    List<JobWebhook> selectActiveByEventAndJob(@Param("eventType") String eventType,
                                                   @Param("jobKey") String jobKey);
}
