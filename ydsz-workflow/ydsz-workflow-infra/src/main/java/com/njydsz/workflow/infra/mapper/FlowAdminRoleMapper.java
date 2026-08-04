package com.njydsz.workflow.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowAdminRole;

/**
 * 流程管理员角色 Mapper（P1-6）
 *
 * <p>对应数据表 <code>ydsz_flow_admin_role</code>，存储流程管理员与流程分类的关联。</p>
 * <p>流程管理员可管理某分类下所有流程（设计/发布/统计），按 (userId + categoryId) 唯一，区别于 RBAC 角色。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_user_category — (userId+categoryId) 唯一索引</li>
 *   <li>idx_category_id — 分类维度查询索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.workflow.domain.entity.FlowAdminRole 流程管理员实体
 * @see com.njydsz.workflow.server.service.FlowAdminService 流程管理员 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowAdminRoleMapper extends BaseMapper<FlowAdminRole> {

    /**
     * 查询用户在指定租户下的所有有效角色。
     */
    List<FlowAdminRole> selectByUserId(@Param("userId") String userId,
                                          @Param("tenantId") String tenantId);

    /**
     * 查询用户是否拥有指定角色。
     */
    FlowAdminRole selectByUserAndRole(@Param("userId") String userId,
                                         @Param("roleCode") String roleCode,
                                         @Param("tenantId") String tenantId);
}
