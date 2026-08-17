package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.infra.entity.CompanyDeptDO;

/**
 * 公司-部门关联表 Mapper
 *
 * <p>对应数据表 <code>ydsz_company_dept</code>，存储公司与部门的多对多关联。
 *
 * <p>支持一个部门归属多个公司（联合公司/合资公司场景），区别于部门的 {@code companyId} 直接归属字段。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_company_dept — (companyId+deptId) 唯一索引
 *   <li>idx_company_id — 公司维度查询索引
 *   <li>idx_dept_id — 部门维度查询索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.infra.entity.CompanyDeptDO 公司-部门关联实体
 * @see com.njydsz.userinfo.server.service.CompanyDeptService 公司-部门 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface CompanyDeptMapper extends BaseMapper<CompanyDeptDO> {}
