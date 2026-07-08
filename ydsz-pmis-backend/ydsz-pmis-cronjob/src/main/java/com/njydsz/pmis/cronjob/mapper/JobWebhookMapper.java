package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobWebhookDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * WebHook 订阅 Mapper（P3-13）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Mapper
public interface JobWebhookMapper extends BaseMapper<JobWebhookDO> {

    /**
     * 查询指定事件类型的活跃 WebHook 列表。
     */
    @Select("SELECT id, name, event_type, job_key, job_group, callback_url, http_method, "
            + "       headers, secret, status, created_at, updated_at, deleted "
            + "FROM pmis_job_webhook "
            + "WHERE event_type = #{eventType} AND status = 'ACTIVE' AND deleted = 0")
    List<JobWebhookDO> selectActiveByEventType(@Param("eventType") String eventType);

    /**
     * 查询指定事件类型且匹配 jobKey 的活跃 WebHook。
     */
    @Select("SELECT id, name, event_type, job_key, job_group, callback_url, http_method, "
            + "       headers, secret, status, created_at, updated_at, deleted "
            + "FROM pmis_job_webhook "
            + "WHERE event_type = #{eventType} AND status = 'ACTIVE' AND deleted = 0 "
            + "  AND (job_key = #{jobKey} OR job_key IS NULL)")
    List<JobWebhookDO> selectActiveByEventAndJob(@Param("eventType") String eventType,
                                                   @Param("jobKey") String jobKey);
}
