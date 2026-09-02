package com.njydsz.common.jdbc.permission;

import java.util.HashSet;
import java.util.Set;

/**
 * 数据范围 ID 扩展器默认实现（原样返回，不扩展下级）。
 *
 * <p>当业务模块未提供 {@link DataScopeIdExpander} 实现时作为默认装配， 保持「接口有落地、行为可预期」：上级维度 ID 不做级联扩展。
 *
 * <p>业务模块需要「本部门及下级部门」等级联数据权限时，应提供自定义实现 覆盖本 Bean（实现 {@link DataScopeIdExpander} 并在容器中注册即可）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NoopDataScopeIdExpander implements DataScopeIdExpander {

  @Override
  public Set<String> expandCompanyIds(Set<String> companyIds) {
    return copyOf(companyIds);
  }

  @Override
  public Set<String> expandDeptIds(Set<String> deptIds) {
    return copyOf(deptIds);
  }

  @Override
  public Set<String> expandProjectIds(Set<String> projectIds) {
    return copyOf(projectIds);
  }

  @Override
  public Set<String> expandRegionIds(Set<String> regionIds) {
    return copyOf(regionIds);
  }

  /** 返回输入的防御性拷贝（null 安全） */
  private static Set<String> copyOf(Set<String> ids) {
    return ids == null ? new HashSet<>(0) : new HashSet<>(ids);
  }
}
