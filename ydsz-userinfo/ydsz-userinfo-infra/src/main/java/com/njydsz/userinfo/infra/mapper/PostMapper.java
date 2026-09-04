package com.njydsz.userinfo.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.userinfo.domain.entity.Post;

/**
 * 岗位 Mapper 接口
 *
 * <p>对应数据表 {@code ydsz_rbac_post}，存储组织架构中的岗位信息。 岗位是「职责维度」，描述用户做什么事（如 PM、DEV、QA），区别于角色（权限维度）。
 *
 * <p><b>本 Mapper 无自定义 SQL：</b>所有查询通过 Service 层使用 MyBatis-Plus 的 {@code LambdaQueryWrapper}
 * 构造。用户-岗位关联由 {@code UserPostMapper} 维护。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>{@code uk_post_code} — 岗位编码唯一索引（{@code postCode} 变更会影响所有引用该岗位的工作流节点）
 *   <li>{@code idx_sort_order} — 排序字段索引（按 {@code sortOrder} 倒序）
 * </ul>
 *
 * <p><b>与其它模块的关联：</b>岗位编码（{@code postCode}）被工作流审批人展开（{@code position:xxx}）使用， 是流程节点配置的关键依赖。
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.userinfo.infra.entity.Post 岗位实体
 * @see com.njydsz.userinfo.infra.mapper.UserPostMapper 用户-岗位关联 Mapper
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {}
