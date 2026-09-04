package com.njydsz.cronjob.infra.mapper.job;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.cronjob.domain.entity.job.JobWebhookRetry;

/**
 * WebHook 重试补偿记录 Mapper（ydsz_job_webhook_retry 表）。
 *
 * <p>注解式 SQL，与模块内其他 Mapper 风格一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface JobWebhookRetryMapper extends BaseMapper<JobWebhookRetry> {

  /**
   * P0-1: 查询待重试的记录（利用 idx_webhook_retry_status_time 复合索引）。
   *
   * <p>复合索引 (status, next_retry_time) 可直接覆盖 WHERE + ORDER BY，避免 filesort。
   *
   * @param now 当前时间
   * @param limit 批量大小
   * @return 待重试记录列表
   */
  @Select(
      "SELECT id, webhook_id, event_type, job_key, log_id, callback_url, "
          + "       http_method, headers, webhook_secret, payload_json, "
          + "       retry_count, max_retries, next_retry_time, "
          + "       retry_status, last_error, last_retry_time, "
          + "       created_at, updated_at "
          + "FROM ydsz_job_webhook_retry "
          + "WHERE retry_status = 'PENDING' AND next_retry_time &lt;= #{now} "
          + "ORDER BY next_retry_time ASC LIMIT #{limit}")
  List<JobWebhookRetry> selectPendingRetries(
      @Param("now") LocalDateTime now, @Param("limit") int limit);

  /**
   * 查询死信记录（超过最大重试次数）。
   *
   * @param limit 批量大小
   * @return 死信记录列表
   */
  @Select(
      "SELECT * FROM ydsz_job_webhook_retry "
          + "WHERE retry_status = 'DEAD' "
          + "ORDER BY updated_at DESC LIMIT #{limit}")
  List<JobWebhookRetry> selectDeadRetries(@Param("limit") int limit);

  /**
   * 更新重试状态和下次重试时间。
   *
   * @param id 记录 ID
   * @param retryCount 当前重试次数
   * @param nextRetryTime 下次重试时间
   * @param lastError 最后错误信息
   * @return 更新行数
   */
  @Update(
      "UPDATE ydsz_job_webhook_retry SET "
          + "retry_count = #{retryCount}, "
          + "next_retry_time = #{nextRetryTime}, "
          + "last_error = #{lastError}, "
          + "last_retry_time = NOW(), "
          + "retry_status = 'PENDING', "
          + "updated_at = NOW() "
          + "WHERE id = #{id}")
  int updateForRetry(
      @Param("id") String id,
      @Param("retryCount") int retryCount,
      @Param("nextRetryTime") LocalDateTime nextRetryTime,
      @Param("lastError") String lastError);

  /**
   * 标记重试成功。
   *
   * @param id 记录 ID
   * @param successTime 成功时间
   * @return 更新行数
   */
  @Update(
      "UPDATE ydsz_job_webhook_retry SET "
          + "retry_status = 'SUCCESS', "
          + "last_retry_time = #{successTime}, "
          + "updated_at = NOW() "
          + "WHERE id = #{id}")
  int markSuccess(@Param("id") String id, @Param("successTime") LocalDateTime successTime);

  /**
   * 标记死信（超出最大重试次数）。
   *
   * @param id 记录 ID
   * @param deadTime 标记时间
   * @param reason 死信原因
   * @return 更新行数
   */
  @Update(
      "UPDATE ydsz_job_webhook_retry SET "
          + "retry_status = 'DEAD', "
          + "last_error = #{reason}, "
          + "last_retry_time = #{deadTime}, "
          + "updated_at = NOW() "
          + "WHERE id = #{id}")
  int markDead(
      @Param("id") String id,
      @Param("deadTime") LocalDateTime deadTime,
      @Param("reason") String reason);

  /**
   * 插入重试记录。
   *
   * @param record 重试记录
   * @return 插入行数
   */
  @Insert(
      "INSERT INTO ydsz_job_webhook_retry ("
          + "id, webhook_id, event_type, job_key, log_id, callback_url, "
          + "http_method, headers, webhook_secret, payload_json, "
          + "retry_count, max_retries, next_retry_time, "
          + "retry_status, created_at, updated_at"
          + ") VALUES ("
          + "#{id}, #{webhookId}, #{eventType}, #{jobKey}, #{logId}, #{callbackUrl}, "
          + "#{httpMethod}, #{headers}, #{webhookSecret}, #{payloadJson}, "
          + "#{retryCount}, #{maxRetries}, #{nextRetryTime}, "
          + "#{retryStatus}, NOW(), NOW())")
  int insertRetry(JobWebhookRetry record);

  /**
   * 统计待重试记录数。
   *
   * @return 待重试记录数
   */
  @Select("SELECT COUNT(*) FROM ydsz_job_webhook_retry WHERE retry_status = 'PENDING'")
  long countPending();

  /**
   * 统计死信记录数。
   *
   * @return 死信记录数
   */
  @Select("SELECT COUNT(*) FROM ydsz_job_webhook_retry WHERE retry_status = 'DEAD'")
  long countDead();
}
