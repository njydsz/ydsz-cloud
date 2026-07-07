package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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
     * 扫描已到触发时间的 NORMAL 任务（P1-7 Leader 模式专用）。
     *
     * <p>使用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 抢占式行锁，
     * 多个 Leader 候选节点并发扫描时互不阻塞，每个节点拿到不同的任务集合。
     * 调用方必须在事务中调用，并立即更新 {@code next_fire_time} 以释放行锁语义。
     *
     * @param now    当前时间（用于判断 next_fire_time &lt;= now）
     * @param limit  单批最多扫描任务数
     * @return 待触发任务列表（已按 next_fire_time 升序排序）
     */
    List<JobDO> selectDueJobs(@Param("now") LocalDateTime now,
                              @Param("limit") int limit);

    /**
     * 原子推进 next_fire_time（P1-7 Leader 模式专用）。
     *
     * <p>Leader 扫描到任务后立即推进 next_fire_time，避免重复派发。
     * 仅当 next_fire_time 未被其他节点推进时才更新成功（CAS 语义）。
     *
     * @param id             任务 ID
     * @param oldNextFireTime 旧的 next_fire_time（CAS 条件）
     * @param newNextFireTime 新的 next_fire_time
     * @param lastFireTime   本次触发时间
     * @return 受影响行数（1=推进成功；0=已被其他节点推进）
     */
    int advanceNextFireTime(@Param("id") String id,
                            @Param("oldNextFireTime") LocalDateTime oldNextFireTime,
                            @Param("newNextFireTime") LocalDateTime newNextFireTime,
                            @Param("lastFireTime") LocalDateTime lastFireTime);

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
    int updateStats(@Param("id") String id,
                    @Param("lastFireTime") LocalDateTime lastFireTime,
                    @Param("nextFireTime") LocalDateTime nextFireTime,
                    @Param("fireCount") Long fireCount,
                    @Param("successCount") Long successCount,
                    @Param("failCount") Long failCount,
                    @Param("status") String status);

    /**
     * P1-6: 重置连续失败计数为 0（任务执行成功时调用）。
     */
    @org.apache.ibatis.annotations.Update("UPDATE pmis_job SET consecutive_fail_count = 0 WHERE id = #{id}")
    int resetConsecutiveFail(@Param("id") String id);

    /**
     * P1-6: 递增连续失败计数（任务执行失败时调用）。
     */
    @org.apache.ibatis.annotations.Update("UPDATE pmis_job SET consecutive_fail_count = consecutive_fail_count + 1 WHERE id = #{id}")
    int incrementConsecutiveFail(@Param("id") String id);

    /**
     * P1-6: 标记任务为 AUTO_PAUSED（熔断自动暂停）。
     */
    @org.apache.ibatis.annotations.Update("UPDATE pmis_job SET status = 'AUTO_PAUSED' WHERE id = #{id} AND status = 'NORMAL'")
    int markAutoPaused(@Param("id") String id);

    /**
     * P1-6: 查询连续失败计数。
     */
    @org.apache.ibatis.annotations.Select("SELECT consecutive_fail_count FROM pmis_job WHERE id = #{id}")
    Integer selectConsecutiveFailCount(@Param("id") String id);
}
