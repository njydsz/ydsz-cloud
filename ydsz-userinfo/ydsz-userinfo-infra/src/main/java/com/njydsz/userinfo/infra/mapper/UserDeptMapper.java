package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.njydsz.userinfo.domain.entity.UserDept;

/**
 * 用户-部门关联表 Mapper
 *
 * <p>对应数据表 <code>ydsz_user_dept</code>，存储用户与部门的多对多关联。</p>
 * <p>支持一人多部门（主岗/兼岗），用 {@code is_main} 标识主部门，是工作流审批人展开（{@code dept:xxx}）的核心数据。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_user_dept — (userId+deptId) 唯一索引</li>
 *   <li>idx_user_id — 用户维度查询索引（查用户的部门）</li>
 *   <li>idx_dept_id — 部门维度查询索引（查部门的用户）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.userinfo.domain.entity.UserDept 用户-部门关联实体
 * @see com.njydsz.userinfo.server.service.UserDeptService 用户-部门 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface UserDeptMapper extends BaseMapper<UserDept> {
}
