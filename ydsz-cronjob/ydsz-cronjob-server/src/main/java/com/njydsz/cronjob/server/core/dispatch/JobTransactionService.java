package com.njydsz.cronjob.server.core.dispatch;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;

/**
 * 任务事务服务（P0-2: 修复 @Transactional 自调用失效）。
 *
 * <p>Spring 基于代理的 AOP 无法拦截同类内部的方法调用（self-invocation）， 当 {@link JobScanner} 内部直接调用 {@code
 * this.acquireDueJobs()} 或 {@code this.advanceNextFireTime()} 时，{@code @Transactional} 注解不生效。
 *
 * <h3>修复方案</h3>
 *
 * <p>将需要事务保护的方法抽取到独立的 Service Bean 中，通过依赖注入调用， 确保 Spring AOP 代理能正确拦截并开启事务。
 *
 * <h3>事务方法</h3>
 *
 * <ul>
 *   <li>{@link #acquireDueJobs(LocalDateTime, int)}：readOnly 事务， 保护 {@code SELECT ... FOR UPDATE
 *       SKIP LOCKED} 抢占式扫描
 *   <li>{@link #advanceNextFireTime(Job, LocalDateTime, LocalDateTime, LocalDateTime)}： read-write
 *       事务，保护 CAS 推进 next_fire_time 的原子性
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobTransactionService {

  private final JobMapper jobMapper;

  /**
   * 抢占式扫描待触发任务（读写事务内）。
   *
   * <p>使用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 抢占式行锁， 多个 Leader 候选节点互不冲突。
   *
   * <p><b>注意</b>：不能标注 {@code readOnly = true}。PostgreSQL 禁止在只读事务中执行
   * {@code SELECT ... FOR UPDATE}（SQLSTATE 25006），且若开启读写分离，只读事务可能被路由到只读副本导致行锁失效。
   *
   * @param now 当前时间
   * @param batchSize 批量大小
   * @return 待触发任务列表
   */
  @Transactional(rollbackFor = Exception.class)
  public List<Job> acquireDueJobs(LocalDateTime now, int batchSize) {
    return jobMapper.selectDueJobs(now, batchSize);
  }

  /**
   * CAS 推进 next_fire_time（read-write 事务内，防止重复派发）。
   *
   * <p>通过 {@code WHERE next_fire_time = #{oldNextFireTime}} 实现 CAS 乐观锁， 保证 Leader 切换时不会重复派发同一任务。
   *
   * @param job 任务定义
   * @param oldNext 原始 next_fire_time（CAS 预期值）
   * @param newNext 新的 next_fire_time
   * @param lastFire 本次触发时间
   * @return true CAS 成功（affected > 0）；false CAS 失败（已被其他节点推进）
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean advanceNextFireTime(
      Job job, LocalDateTime oldNext, LocalDateTime newNext, LocalDateTime lastFire) {
    if (oldNext == null) {
      log.warn("[JobTx] next_fire_time 为 null, 跳过 CAS: key={}", job.getJobKey());
      return false;
    }
    int affected = jobMapper.advanceNextFireTime(job.getId(), oldNext, newNext, lastFire);
    return affected > 0;
  }
}
