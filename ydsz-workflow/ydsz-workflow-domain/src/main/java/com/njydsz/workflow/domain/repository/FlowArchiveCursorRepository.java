package com.njydsz.workflow.domain.repository;

import com.njydsz.workflow.domain.entity.FlowArchiveCursor;
import com.njydsz.workflow.domain.vo.FlowArchiveCursorVO;

/**
 * 流程归档游标仓储接口（Domain 层契约）。
 *
 * <p>定义归档断点续传游标的持久化操作，实现位于 {@code infra} 层。
 *
 * <p>查询方法返回 {@link FlowArchiveCursorVO}，禁止将 Entity 泄露到 server 层（DDD-007）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
public interface FlowArchiveCursorRepository {

  /**
   * 按归档类型 + 租户查询游标。
   *
   * @param archiveType 归档类型
   * @param tenantId 租户 ID
   * @return 游标视图对象；不存在返回 null
   */
  FlowArchiveCursorVO findByTypeAndTenant(String archiveType, String tenantId);

  /**
   * 保存或更新游标（upsert 语义）。
   *
   * @param cursor 游标记录
   */
  void saveOrUpdate(FlowArchiveCursor cursor);
}
