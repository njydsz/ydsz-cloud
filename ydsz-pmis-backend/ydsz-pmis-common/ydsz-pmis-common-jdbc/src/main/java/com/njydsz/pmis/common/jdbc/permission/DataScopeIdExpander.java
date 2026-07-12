package com.njydsz.pmis.common.jdbc.permission;

/**
 * 数据范围ID扩展器接口
 *
 * <p>定义数据权限范围ID的扩展方法，用于将公司、部门、项目、区域等
 * 上级维度的ID扩展为下级维度的ID集合，实现数据权限的级联查询。
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 将公司ID扩展为包含其所有子部门的ID集合
 * Set&lt;String&gt; deptIds = expander.expandCompanyIds(companyIds);
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */

import java.util.Set;

public interface DataScopeIdExpander {
    /**
     * 将公司ID集合扩展为下级维度ID集合
     *
     * @param companyIds 公司ID集合
     * @return 扩展后的ID集合
     */
    Set<String> expandCompanyIds(Set<String> companyIds);

    /**
     * 将部门ID集合扩展为下级维度ID集合
     *
     * @param deptIds 部门ID集合
     * @return 扩展后的ID集合
     */
    Set<String> expandDeptIds(Set<String> deptIds);

    /**
     * 将项目ID集合扩展为下级维度ID集合
     *
     * @param projectIds 项目ID集合
     * @return 扩展后的ID集合
     */
    Set<String> expandProjectIds(Set<String> projectIds);

    /**
     * 将区域ID集合扩展为下级维度ID集合
     *
     * @param regionIds 区域ID集合
     * @return 扩展后的ID集合
     */
    Set<String> expandRegionIds(Set<String> regionIds);
}

