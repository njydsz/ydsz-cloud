package com.njydsz.cronjob.infra.mapper.job;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.cronjob.domain.entity.job.JobWebhook;

/**
 * 任务 Webhook Mapper
 *
 * <p>对应数据表 <code>ydsz_job_webhook</code>。
 * <p>Webhook 在任务成功/失败/完成时回调外部系统（OA/IM 群/工单系统），用于任务执行结果同步。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_job_event — (任务+事件类型) 唯一索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.cronjob.domain.entity.job.JobWebhook Webhook 实体
 * @see com.njydsz.cronjob.server.service.JobWebhookService Webhook Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobWebhookMapper extends BaseMapper<JobWebhook> {

    /**
     * 查询指定事件类型的全部活跃 WebHook 列表。
     *
     * <p>仅返回 {@code status = 'ACTIVE'} 且未逻辑删除的记录，按事件类型拉取回调配置，
     * 供 {@code WebhookEventDispatcher} 在对应事件触发时批量推送。
     *
     * @param eventType 事件类型（如 SUCCESS / FAILURE / COMPLETED）
     * @return 活跃 WebHook 列表；无配置时返回空列表而非 null
     */
    @Select("SELECT id, name, event_type, job_key, job_group, callback_url, http_method, "
            + "       headers, secret, status, created_at, updated_at, deleted "
            + "FROM ydsz_job_webhook "
            + "WHERE event_type = #{eventType} AND status = 'ACTIVE' AND deleted = 0")
    List<JobWebhook> selectActiveByEventType(@Param("eventType") String eventType);

    /**
     * 查询指定事件类型且匹配任务 KEY 的活跃 WebHook。
     *
     * <p>{@code job_key} 为 null 的记录视为「全局 WebHook」，对所有任务生效；
     * 因此查询条件包含 {@code job_key = #{jobKey} OR job_key IS NULL}，
     * 保证任务专属回调与全局回调都能命中。
     *
     * @param eventType 事件类型
     * @param jobKey    任务 KEY（用于匹配任务专属 WebHook）
     * @return 命中的活跃 WebHook 列表；未命中时返回空列表
     */
    @Select("SELECT id, name, event_type, job_key, job_group, callback_url, http_method, "
            + "       headers, secret, status, created_at, updated_at, deleted "
            + "FROM ydsz_job_webhook "
            + "WHERE event_type = #{eventType} AND status = 'ACTIVE' AND deleted = 0 "
            + "  AND (job_key = #{jobKey} OR job_key IS NULL)")
    List<JobWebhook> selectActiveByEventAndJob(@Param("eventType") String eventType,
                                                   @Param("jobKey") String jobKey);
}
