package com.njydsz.common.jdbc.permission;

import java.util.Set;

/**
 * 数据范围 ID 扩展器接口。
 *
 * <p>定义数据权限范围 ID 的扩展方法，用于将公司、部门、项目、区域等 上级维度的 ID 扩展为下级维度的 ID 集合，实现数据权限的级联查询。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * // 将公司ID扩展为包含其所有子部门的ID集合
 * Set&lt;String&gt; deptIds = expander.expandCompanyIds(companyIds);
 * </pre>
 *
 * <p>未提供实现时由 {@link NoopDataScopeIdExpander} 兜底（原样返回，不扩展下级）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see NoopDataScopeIdExpander
 */
public interface DataScopeIdExpander {

  /**
   * 将公司 ID 集合扩展为下级维度 ID 集合。
   *
   * @param companyIds 公司 ID 集合
   * @return 扩展后的 ID 集合
   */
  Set<String> expandCompanyIds(Set<String> companyIds);

  /**
   * 将部门 ID 集合扩展为下级维度 ID 集合。
   *
   * @param deptIds 部门 ID 集合
   * @return 扩展后的 ID 集合
   */
  Set<String> expandDeptIds(Set<String> deptIds);

  /**
   * 将项目 ID 集合扩展为下级维度 ID 集合。
   *
   * @param projectIds 项目 ID 集合
   * @return 扩展后的 ID 集合
   */
  Set<String> expandProjectIds(Set<String> projectIds);

  /**
   * 将区域 ID 集合扩展为下级维度 ID 集合。
   *
   * @param regionIds 区域 ID 集合
   * @return 扩展后的 ID 集合
   */
  Set<String> expandRegionIds(Set<String> regionIds);
}
