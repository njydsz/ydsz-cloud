package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.infra.entity.Department;

/**
 * 部门 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_org_department}，存储组织架构中的部门信息。 部门是组织架构的核心节点，支持无限级树形结构（{@code parentId="0"} =
 * 根部门）。
 *
 * <p><b>本 Mapper 无自定义 SQL：</b>所有查询通过 Service 层使用 MyBatis-Plus 的 {@code LambdaQueryWrapper}
 * 构造。部门树形结构由 Service 层在内存中递归构建（避免 N+1 查询）。 部门-用户关联由 {@code UserDeptMapper} 维护。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>{@code uk_dept_code} — 部门编码唯一索引
 *   <li>{@code idx_parent_id} — 父级 ID 索引（树形查询）
 *   <li>{@code idx_leader_id} — 部门负责人 ID 索引（供工作流 {@code dept:xxx} 审批人展开调用）
 *   <li>{@code idx_sort_order} — 排序字段索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.userinfo.infra.entity.Department 部门实体
 * @see com.njydsz.userinfo.infra.mapper.UserDeptMapper 用户-部门关联 Mapper
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {}
