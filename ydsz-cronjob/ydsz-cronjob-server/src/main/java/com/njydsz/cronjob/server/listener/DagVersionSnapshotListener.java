package com.njydsz.cronjob.server.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.njydsz.cronjob.domain.dag.DagVersionSnapshotEvent;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.repository.JobDagVersionRepository;
import com.njydsz.cronjob.infra.entity.dag.JobDag;
import com.njydsz.cronjob.infra.entity.dag.JobDagVersion;

/**
 * DAG 版本快照事件监听器 — 在主写事务提交后异步创建版本快照。
 *
 * <p><b>设计意图（云顶编码规范 35.2 版本快照异步化）：</b>
 *
 * <ul>
 *   <li>通过 {@link TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)} 保证仅在主事务<b>成功提交</b>后触发，
 *       避免回滚事务产生垃圾快照记录
 *   <li>主事务不再包含版本快照的 DB 写入，缩短持锁时间，降低写操作延迟
 *   <li>快照创建异常被隔离捕获，不影响主业务（主事务已提交成功）
 * </ul>
 *
 * <p><b>注意：</b>若快照创建失败，仅日志告警，不回滚已提交的主业务数据。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DagVersionSnapshotEvent DAG 版本快照事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagVersionSnapshotListener {

  /** DAG 定义 Repository */
  private final JobDagRepository jobDagRepository;

  /** DAG 版本历史 Repository */
  private final JobDagVersionRepository jobDagVersionRepository;

  /**
   * 事务提交后异步创建 DAG 版本快照。
   *
   * <p>异常被隔离捕获，仅日志告警，不回滚已提交的主业务数据（《云顶编码规范》27.3 事件隔离原则）。
   *
   * @param event DAG 版本快照事件（含 DAG ID 和版本备注）
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDagVersionSnapshot(DagVersionSnapshotEvent event) {
    try {
      String dagId = event.getDagId();
      JobDag dag = jobDagRepository.selectById(dagId);
      if (dag == null) {
        log.error(
            "[DagVersionSnapshotListener] DAG 不存在，跳过快照创建: dagId={}",
            dagId);
        return;
      }
      JobDagVersion versionDO = new JobDagVersion();
      versionDO.setDagId(dag.getId());
      versionDO.setDagKey(dag.getDagKey());
      versionDO.setVersion(dag.getVersion());
      versionDO.setDagDefinition(dag.getDagDefinition());
      versionDO.setDagName(dag.getDagName());
      versionDO.setTriggerType(dag.getTriggerType());
      versionDO.setCronExpression(dag.getCronExpression());
      versionDO.setFailStrategy(dag.getFailStrategy());
      versionDO.setRemark(event.getRemark());
      jobDagVersionRepository.insert(versionDO);
      log.debug(
          "[DagVersionSnapshotListener] DAG 版本快照创建成功: dagId={}, dagKey={}, version={}",
          dag.getId(),
          dag.getDagKey(),
          dag.getVersion());
    } catch (Exception e) {
      // 快照创建失败不回滚主业务（主事务已提交），仅日志告警便于人工补偿
      log.error(
          "[DagVersionSnapshotListener] DAG 版本快照创建失败（需人工补偿）: dagId={}, remark={}, error={}",
          event.getDagId(),
          event.getRemark(),
          e.getMessage(),
          e);
    }
  }
}
