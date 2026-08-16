package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 公司-部门关联实体
 *
 * <p>对应数据库表 {@code ydsz_company_dept}，是连接公司与部门的多对多中间表。 一个部门可被多个公司共享（如「研发中心」归属于集团总部和子公司），
 * 一个公司可包含多个部门（含跨公司调岗场景）。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>采用「关系实体」模式（带审计字段），便于追溯归属关系变更
 *   <li>由 {@code CompanyDeptController} 维护（批量绑定/解绑）
 *   <li>主归属逻辑由 {@link Department#getLeaderId()} 维护（非本中间表）
 * </ul>
 *
 * <p><b>与 {@link UserDept} 的区别：</b>
 *
 * <ul>
 *   <li>{@code CompanyDept}：组织结构维度（公司 → 部门）
 *   <li>{@code UserDept}：人员归属维度（用户 → 部门）
 *   <li>两者正交，可独立维护
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 查询某公司所有部门 ID
 * List<String> deptIds = companyDeptMapper.selectList(
 *     new LambdaQueryWrapper<CompanyDept>().eq(CompanyDept::getCompanyId, companyId)
 * ).stream().map(CompanyDept::getDeptId).collect(Collectors.toList());
 * }</pre>
 *
 * <p><b>索引设计：</b>普通索引 {@code idx_company_id}（{@code company_id}）、 {@code idx_dept_id}（{@code
 * dept_id}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see Company 公司实体
 * @see Department 部门实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_company_dept")
public class CompanyDept extends MpBaseEntity<String> {

  /** 公司 ID，关联 {@link Company#getId()} */
  private String companyId;

  /** 部门 ID，关联 {@link Department#getId()} */
  private String deptId;
}
