package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.infra.entity.Company;

/**
 * 公司 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_company}，存储集团-子公司多级组织信息。 支持多级父子关系（{@code parentId="0"} =
 * 顶级公司），一个公司可包含多个部门（通过 {@code CompanyDept} 维护）。
 *
 * <p><b>本 Mapper 无自定义 SQL：</b>所有查询通过 Service 层使用 MyBatis-Plus 的 {@code LambdaQueryWrapper}
 * 构造。公司-部门关联由 {@code CompanyDeptMapper} 维护。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>{@code uk_company_code} — 公司编码唯一索引
 *   <li>{@code idx_parent_id} — 父级 ID 索引（树形查询）
 *   <li>{@code idx_status} — 状态索引（启用/禁用过滤）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件。
 *
 * <p><b>与其它模块的关联：</b>
 *
 * <ul>
 *   <li>用户多租户隔离：公司是租户的物理边界
 *   <li>财务结算：{@code ydsz_finance} 跨公司数据按公司维度归集
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.infra.entity.Company 公司实体
 * @see com.njydsz.userinfo.infra.mapper.CompanyDeptMapper 公司-部门关联 Mapper
 */
@Mapper
public interface CompanyMapper extends BaseMapper<Company> {}
