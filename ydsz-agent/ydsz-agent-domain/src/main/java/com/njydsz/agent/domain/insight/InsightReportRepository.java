package com.njydsz.agent.domain.insight;

import java.util.List;
import java.util.Optional;

/**
 * 洞察报告仓储接口（领域层）。
 *
 * <p>定义洞察报告持久化的领域契约，实现位于 infra 层（基于 MyBatis-Plus + 数据库）。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public interface InsightReportRepository {

  /**
   * 保存或更新报告实体。
   *
   * <p>当实体 {@link InsightReport#getId()} 为 null 时执行插入，否则执行更新。
   *
   * @param report 报告实体
   */
  void save(InsightReport report);

  /**
   * 根据业务 ID 查找报告。
   *
   * @param reportId 报告唯一业务 ID
   * @return 报告实体（可能为空）
   */
  Optional<InsightReport> findById(String reportId);

  /**
   * 根据用户 ID 查询近期报告（按创建时间倒序）。
   *
   * @param userId 用户 ID
   * @param limit 返回条数上限
   * @return 报告实体列表
   */
  List<InsightReport> findByUserId(String userId, int limit);

  /**
   * 根据业务 ID 删除报告。
   *
   * @param reportId 报告唯一业务 ID
   */
  void deleteById(String reportId);
}
