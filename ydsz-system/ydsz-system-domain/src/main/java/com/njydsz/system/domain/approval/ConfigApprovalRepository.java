package com.njydsz.system.domain.approval;

import java.util.List;

/**
 * 配置变更审批单 Repository 接口。
 *
 * <p>定义审批单的持久化操作契约，实现位于 infra 层。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public interface ConfigApprovalRepository {

  /**
   * 保存审批单。
   *
   * @param record 审批单实体
   * @return 是否保存成功
   */
  boolean save(ConfigApproval record);

  /**
   * 更新审批单状态。
   *
   * @param record 审批单实体（含 id 和待更新字段）
   * @return 是否更新成功
   */
  boolean updateStatus(ConfigApproval record);

  /**
   * 根据 ID 查询审批单。
   *
   * @param id 审批单 ID
   * @return 审批单实体，不存在时返回 null
   */
  ConfigApproval findById(String id);

  /**
   * 分页查询审批单。
   *
   * @param query 查询参数
   * @return 审批单列表
   */
  List<ConfigApproval> findByQuery(ConfigApprovalQuery query);

  /**
   * 统计满足条件的审批单数量。
   *
   * @param query 查询参数
   * @return 记录总数
   */
  long countByQuery(ConfigApprovalQuery query);
}
