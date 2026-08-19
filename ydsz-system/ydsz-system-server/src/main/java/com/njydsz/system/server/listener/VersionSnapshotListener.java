package com.njydsz.system.server.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.njydsz.system.domain.event.VersionSnapshotEvent;
import com.njydsz.system.server.service.EntityVersionService;

/**
 * 版本快照事件监听器 — 在主写事务提交后异步创建版本快照。
 *
 * <p><b>设计意图（P3-2 版本快照异步化）：</b>
 *
 * <ul>
 *   <li>通过 {@link TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)} 保证仅在主事务<b>成功提交</b>后触发，
 *       避免回滚事务产生垃圾快照记录
 *   <li>主事务不再包含版本快照的 DB 写入，缩短持锁时间，降低写操作延迟
 *   <li>快照创建异常被隔离捕获，不影响主业务（主事务已提交成功）
 * </ul>
 *
 * <p><b>注意：</b>本监听器执行在新事务中（{@link EntityVersionService#createVersion} 自带 {@code @Transactional}），
 * 若快照创建失败，仅日志告警，不回滚已提交的主业务数据。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see VersionSnapshotEvent 版本快照事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionSnapshotListener {

  /** 统一实体版本服务 */
  private final EntityVersionService entityVersionService;

  /**
   * 事务提交后创建版本快照。
   *
   * <p>异常被隔离捕获，仅日志告警，不回滚已提交的主业务数据（《云顶编码规范》27.3 事件隔离原则）。
   *
   * @param event 版本快照事件（含版本创建参数）
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCreateVersionSnapshot(VersionSnapshotEvent event) {
    try {
      EntityVersionService entityVersionService = this.entityVersionService;
      String versionId = entityVersionService.createVersion(event.getVersionDto());
      log.debug(
          "[VersionSnapshotListener] 版本快照创建成功: resourceType={}, resourceKey={}, versionId={}",
          event.getVersionDto().getResourceType(),
          event.getVersionDto().getResourceKey(),
          versionId);
    } catch (Exception e) {
      // 快照创建失败不回滚主业务（主事务已提交），仅日志告警便于人工补偿
      log.error(
          "[VersionSnapshotListener] 版本快照创建失败（需人工补偿）: resourceType={}, resourceKey={}, error={}",
          event.getVersionDto().getResourceType(),
          event.getVersionDto().getResourceKey(),
          e.getMessage(),
          e);
    }
  }
}
