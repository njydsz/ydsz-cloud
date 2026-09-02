package com.njydsz.cronjob.domain.dag;

import org.springframework.context.ApplicationEvent;

/**
 * DAG 版本快照创建事件 — DAG 定义写操作（create/update/rollback）成功后发布，由监听器在事务提交后异步创建版本快照。
 *
 * <p><b>设计意图（云顶编码规范 35.2 版本快照异步化）：</b>
 *
 * <ul>
 *   <li>将版本快照创建从主写操作事务中剥离，缩短主事务持锁时间，降低写操作延迟
 *   <li>使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 保证仅在主事务提交成功后创建快照，
 *       避免回滚事务产生垃圾快照记录
 *   <li>快照创建失败不影响主业务（监听器内部捕获异常仅日志告警）
 * </ul>
 *
 * <p><b>发布时机：</b>由 {@code JobDagServiceImpl} 在 DAG 定义写操作成功后、事务提交前发布。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class DagVersionSnapshotEvent extends ApplicationEvent {

  /** DAG 定义 ID */
  private final String dagId;

  /** 版本备注（如 "初始创建"、"更新 DAG 定义"、"回滚到版本 V2"） */
  private final String remark;

  /**
   * 构造 DAG 版本快照事件。
   *
   * @param source 事件源（通常为发布者的 {@code this} 引用）
   * @param dagId DAG 定义 ID
   * @param remark 版本备注
   */
  public DagVersionSnapshotEvent(Object source, String dagId, String remark) {
    super(source);
    this.dagId = dagId;
    this.remark = remark;
  }

  /**
   * 获取 DAG 定义 ID。
   *
   * @return DAG 定义 ID
   */
  public String getDagId() {
    return dagId;
  }

  /**
   * 获取版本备注。
   *
   * @return 版本备注
   */
  public String getRemark() {
    return remark;
  }
}
