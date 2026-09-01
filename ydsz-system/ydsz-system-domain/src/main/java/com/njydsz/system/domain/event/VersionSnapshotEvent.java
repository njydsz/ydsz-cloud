package com.njydsz.system.domain.event;

import com.njydsz.system.domain.dto.EntityVersionDTO;

/**
 * 版本快照创建事件 — 资源写操作（update/remove）成功后发布，由监听器在事务提交后异步创建版本快照。
 *
 * <p><b>设计意图（P3-2 版本快照异步化）：</b>
 *
 * <ul>
 *   <li>将版本快照创建从主写操作事务中剥离，缩短主事务持锁时间，降低写操作延迟
 *   <li>使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 保证仅在主事务提交成功后创建快照，
 *       避免回滚事务产生垃圾快照记录
 *   <li>快照创建失败不影响主业务（监听器内部捕获异常仅日志告警）
 * </ul>
 *
 * <p><b>发布时机：</b>由业务 Service（如 {@code ConfigServiceImpl}）在写操作成功后、事务提交前发布。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see EntityVersionDTO 版本创建参数 DTO
 */
public class VersionSnapshotEvent {

  /** 事件源（通常为发布者的 {@code this} 引用） */
  private final Object source;

  /** 版本创建参数（含资源类型/键/分组/版本号/变更说明/快照 JSON） */
  private final EntityVersionDTO versionDto;

  /**
   * 构造版本快照事件。
   *
   * @param source 事件源（通常为发布者的 {@code this} 引用）
   * @param versionDto 版本创建参数
   */
  public VersionSnapshotEvent(Object source, EntityVersionDTO versionDto) {
    this.source = source;
    this.versionDto = versionDto;
  }

  /**
   * 获取事件源。
   *
   * @return 事件源对象
   */
  public Object getSource() {
    return source;
  }

  /**
   * 获取版本创建参数。
   *
   * @return 版本创建参数 DTO
   */
  public EntityVersionDTO getVersionDto() {
    return versionDto;
  }
}
